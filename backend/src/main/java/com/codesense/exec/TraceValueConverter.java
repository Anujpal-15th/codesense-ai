package com.codesense.exec;

import com.sun.jdi.ArrayReference;
import com.sun.jdi.ArrayType;
import com.sun.jdi.Field;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.PrimitiveValue;
import com.sun.jdi.StringReference;
import com.sun.jdi.Value;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Converts live JDI {@link Value} handles into serializable {@link TraceValue}s,
 * bounded by {@link TraceLimits} and safe against cyclic object graphs.
 *
 * <p>For the common {@code java.util} collections a <em>semantic</em> view is
 * produced (real key -> value entries / elements) by reading the collection's
 * internal fields directly - {@code HashMap.table}, {@code TreeMap.root},
 * {@code ArrayList.elementData} - rather than dumping every raw field.
 *
 * <p><b>Why field reads, not method invocation.</b> The original plan was to
 * invoke {@code entrySet().toArray()} on the debuggee (general across all Map
 * implementations). In this environment (JDK 25 + the local-process JDWP
 * sandbox) {@code ObjectReference.invokeMethod} reproducibly and immediately
 * disconnected the debuggee VM on the first call - which is unrecoverable (the
 * whole trace dies), so the intended graceful fallback could not save it.
 * Field reads use only the same JDWP field-access operations the rest of this
 * converter already relies on, so they are guaranteed compatible and side-effect
 * free. The trade-off is that each collection family needs its own internal
 * layout knowledge, and unrecognized/failed cases fall back to the raw
 * {@link TraceValue.ObjectSummary} - a pure enhancement, never a regression.
 *
 * <p>Treeified HashMap bins are handled transparently: HashMap keeps the
 * {@code next} linkage even in {@code TreeNode} bins (for untreeify), so walking
 * each bucket's {@code next} chain yields all entries regardless of treeification.
 *
 * <p><b>Two separate depth budgets, not one.</b> {@code depth} bounds genuine
 * object-nesting (an object containing an unrelated object containing another
 * unrelated object...) and stays intentionally tight ({@link TraceLimits#maxObjectDepth()},
 * default 4) - that shape is rare and a deep one is usually a real bug being
 * chased. {@code chainLength} bounds a <em>same-type self-referential</em> edge -
 * a {@code ListNode.next} pointing to another {@code ListNode}, a
 * {@code TreeNode.left}/{@code right} pointing to another {@code TreeNode} -
 * which is structurally just a longer sequence, not deeper nesting, so it
 * shares {@link TraceLimits#maxArrayElements()} (the same "how many" budget
 * arrays/collections already get) instead of the tight object-depth one.
 * Without this split, a completely ordinary 6-7 node linked list or a modest
 * BST would have its tail silently truncated, because walking {@code next}/
 * {@code left}/{@code right} used to consume the same narrow budget meant for
 * unrelated nested objects. {@link #convertRawObject} is the only place that
 * decides which budget an edge draws from - every other recursion site just
 * threads {@code chainLength} through unchanged.
 */
class TraceValueConverter {

    private final TraceLimits limits;

    TraceValueConverter(TraceLimits limits) {
        this.limits = limits;
    }

    TraceValue convert(Value value) {
        return convert(value, 0, 0, new HashSet<>());
    }

    private TraceValue convert(Value value, int depth, int chainLength, Set<Long> inProgress) {
        if (value == null) {
            return new TraceValue.NullVal();
        }
        if (depth > limits.maxObjectDepth() || chainLength > limits.maxArrayElements()) {
            return new TraceValue.Truncated("max-depth-exceeded");
        }
        if (value instanceof PrimitiveValue pv) {
            return new TraceValue.Primitive(pv.type().name(), pv.toString());
        }
        if (value instanceof StringReference sr) {
            String s = sr.value();
            boolean tooLong = s.length() > limits.maxStringLength();
            return new TraceValue.StringVal(tooLong ? s.substring(0, limits.maxStringLength()) : s, tooLong);
        }
        if (value instanceof ArrayReference ar) {
            return convertArray(ar, depth, chainLength, inProgress);
        }
        if (value instanceof ObjectReference or) {
            return convertObject(or, depth, chainLength, inProgress);
        }
        return new TraceValue.Truncated("unsupported-value-type:" + value.type().name());
    }

    private TraceValue convertArray(ArrayReference ar, int depth, int chainLength, Set<Long> inProgress) {
        int length = ar.length();
        int limit = Math.min(length, limits.maxArrayElements());
        List<Value> values = length == 0 ? List.of() : ar.getValues(0, limit);
        List<TraceValue> elements = new ArrayList<>();
        for (Value v : values) {
            elements.add(convert(v, depth + 1, chainLength, inProgress));
        }
        String componentType = ar.referenceType() instanceof ArrayType at
                ? at.componentTypeName()
                : "unknown";
        return new TraceValue.ArraySummary(componentType, length, elements, length > limit);
    }

    private TraceValue convertObject(ObjectReference or, int depth, int chainLength, Set<Long> inProgress) {
        long id = or.uniqueID();
        if (inProgress.contains(id)) {
            return new TraceValue.ObjectSummary(or.referenceType().name(), String.valueOf(id), List.of(), true);
        }
        inProgress.add(id);
        try {
            TraceValue semantic = tryConvertCollection(or, id, depth, chainLength, inProgress);
            if (semantic != null) {
                return semantic;
            }
            return convertRawObject(or, id, depth, chainLength, inProgress);
        } finally {
            inProgress.remove(id);
        }
    }

    private TraceValue convertRawObject(ObjectReference or, long id, int depth, int chainLength, Set<Long> inProgress) {
        List<Field> instanceFields = or.referenceType().allFields().stream()
                .filter(f -> !f.isStatic())
                .toList();
        int limit = Math.min(instanceFields.size(), limits.maxObjectFields());
        List<VariableSnapshot> fields = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            Field field = instanceFields.get(i);
            Value fieldValue = or.getValue(field);
            // Same concrete type as the container -> a chain/tree edge (next,
            // left, right, ...), draws from chainLength instead of depth. A
            // different type is genuine nesting and still draws from depth.
            boolean sameTypeChain = fieldValue instanceof ObjectReference fieldObj
                    && fieldObj.referenceType().equals(or.referenceType());
            int nextDepth = sameTypeChain ? depth : depth + 1;
            int nextChainLength = sameTypeChain ? chainLength + 1 : chainLength;
            fields.add(new VariableSnapshot(field.name(), field.typeName(),
                    convert(fieldValue, nextDepth, nextChainLength, inProgress)));
        }
        return new TraceValue.ObjectSummary(or.referenceType().name(), String.valueOf(id), fields, instanceFields.size() > limit);
    }

    // ------------------------------------------------------------------
    // Semantic collection views (field-reflection). Return null to signal
    // "not a recognized collection / could not read it" -> raw-field fallback.
    // ------------------------------------------------------------------

    private TraceValue tryConvertCollection(ObjectReference or, long id, int depth, int chainLength, Set<Long> inProgress) {
        String type = or.referenceType().name();
        try {
            switch (type) {
                case "java.util.HashMap":
                case "java.util.LinkedHashMap":
                    return hashMapView(or, type, id, depth, chainLength, inProgress);
                case "java.util.TreeMap":
                    return treeMapView(or, type, id, depth, chainLength, inProgress);
                case "java.util.HashSet":
                case "java.util.LinkedHashSet":
                    return backingMapSetView(or, type, id, depth, chainLength, inProgress, "map");
                case "java.util.TreeSet":
                    return backingMapSetView(or, type, id, depth, chainLength, inProgress, "m");
                case "java.util.ArrayList":
                case "java.util.Vector":
                    return arrayListView(or, type, id, depth, chainLength, inProgress);
                default:
                    return null;
            }
        } catch (RuntimeException e) {
            // Any JDI read hiccup -> fall back to the raw dump.
            return null;
        }
    }

    /** HashMap / LinkedHashMap: walk table[] buckets, following each Node.next chain. */
    private TraceValue hashMapView(ObjectReference or, String type, long id, int depth, int chainLength, Set<Long> inProgress) {
        List<TraceValue.MapSummary.Entry> entries = new ArrayList<>();
        int total = collectHashMapEntries(or, entries, depth, chainLength, inProgress);
        if (total < 0) {
            return null;
        }
        return new TraceValue.MapSummary(type, String.valueOf(id), total, entries, total > entries.size());
    }

    /**
     * Collects entries from a HashMap-shaped object into {@code out} (bounded by
     * maxArrayElements). Returns the map's true size, or -1 if the layout wasn't
     * as expected (signals fallback).
     */
    private int collectHashMapEntries(ObjectReference mapObj, List<TraceValue.MapSummary.Entry> out, int depth, int chainLength, Set<Long> inProgress) {
        int size = readIntField(mapObj, "size", -1);
        if (size < 0) {
            return -1;
        }
        Value tableValue = readField(mapObj, "table");
        if (tableValue == null) {
            return size; // size 0 (or lazily-null table) -> no entries
        }
        if (!(tableValue instanceof ArrayReference table)) {
            return -1;
        }
        int cap = table.length();
        List<Value> buckets = cap == 0 ? List.of() : table.getValues(0, cap);
        for (Value bucket : buckets) {
            Value node = bucket;
            while (node instanceof ObjectReference nodeRef) {
                if (out.size() >= limits.maxArrayElements()) {
                    return size;
                }
                Value key = readField(nodeRef, "key");
                Value val = readField(nodeRef, "value");
                out.add(new TraceValue.MapSummary.Entry(
                        convert(key, depth + 1, chainLength, inProgress),
                        convert(val, depth + 1, chainLength, inProgress)));
                node = readField(nodeRef, "next");
            }
        }
        return size;
    }

    /** TreeMap: in-order walk of the red-black tree from root. */
    private TraceValue treeMapView(ObjectReference or, String type, long id, int depth, int chainLength, Set<Long> inProgress) {
        int size = readIntField(or, "size", -1);
        if (size < 0) {
            return null;
        }
        List<TraceValue.MapSummary.Entry> entries = new ArrayList<>();
        Value root = readField(or, "root");
        if (root instanceof ObjectReference rootRef) {
            walkTreeMap(rootRef, entries, depth, chainLength, inProgress);
        }
        return new TraceValue.MapSummary(type, String.valueOf(id), size, entries, size > entries.size());
    }

    private void walkTreeMap(ObjectReference node, List<TraceValue.MapSummary.Entry> out, int depth, int chainLength, Set<Long> inProgress) {
        if (out.size() >= limits.maxArrayElements()) {
            return;
        }
        Value left = readField(node, "left");
        if (left instanceof ObjectReference leftRef) {
            walkTreeMap(leftRef, out, depth, chainLength, inProgress);
        }
        if (out.size() >= limits.maxArrayElements()) {
            return;
        }
        Value key = readField(node, "key");
        Value val = readField(node, "value");
        out.add(new TraceValue.MapSummary.Entry(
                convert(key, depth + 1, chainLength, inProgress),
                convert(val, depth + 1, chainLength, inProgress)));
        Value right = readField(node, "right");
        if (right instanceof ObjectReference rightRef) {
            walkTreeMap(rightRef, out, depth, chainLength, inProgress);
        }
    }

    /** HashSet/LinkedHashSet/TreeSet: elements are the KEYS of a backing map field. */
    private TraceValue backingMapSetView(ObjectReference or, String type, long id, int depth, int chainLength, Set<Long> inProgress, String backingField) {
        Value backing = readField(or, backingField);
        if (!(backing instanceof ObjectReference mapObj)) {
            return null;
        }
        String mapType = mapObj.referenceType().name();
        List<TraceValue.MapSummary.Entry> entries = new ArrayList<>();
        int total;
        if (mapType.equals("java.util.TreeMap")) {
            total = readIntField(mapObj, "size", -1);
            Value root = readField(mapObj, "root");
            if (root instanceof ObjectReference rootRef) {
                walkTreeMap(rootRef, entries, depth, chainLength, inProgress);
            }
        } else {
            total = collectHashMapEntries(mapObj, entries, depth, chainLength, inProgress);
        }
        if (total < 0) {
            return null;
        }
        List<TraceValue> elements = new ArrayList<>(entries.size());
        for (TraceValue.MapSummary.Entry e : entries) {
            elements.add(e.key());
        }
        return new TraceValue.SetSummary(type, String.valueOf(id), total, elements, total > elements.size());
    }

    /** ArrayList / Vector: the first {@code size} slots of elementData[]. */
    private TraceValue arrayListView(ObjectReference or, String type, long id, int depth, int chainLength, Set<Long> inProgress) {
        int size = readIntField(or, "elementCount", -1);
        if (size < 0) {
            size = readIntField(or, "size", -1); // ArrayList uses "size", Vector "elementCount"
        }
        if (size < 0) {
            return null;
        }
        Value dataValue = readField(or, "elementData");
        if (!(dataValue instanceof ArrayReference data)) {
            return dataValue == null && size == 0
                    ? new TraceValue.ListSummary(type, String.valueOf(id), 0, List.of(), false)
                    : null;
        }
        int limit = Math.min(size, limits.maxArrayElements());
        limit = Math.min(limit, data.length());
        List<Value> values = limit == 0 ? List.of() : data.getValues(0, limit);
        List<TraceValue> elements = new ArrayList<>();
        for (Value v : values) {
            elements.add(convert(v, depth + 1, chainLength, inProgress));
        }
        return new TraceValue.ListSummary(type, String.valueOf(id), size, elements, size > elements.size());
    }

    // ------------------------------------------------------------------
    // Field-read helpers
    // ------------------------------------------------------------------

    private Value readField(ObjectReference or, String fieldName) {
        Field field = or.referenceType().fieldByName(fieldName);
        return field != null ? or.getValue(field) : null;
    }

    private int readIntField(ObjectReference or, String fieldName, int fallback) {
        Value v = readField(or, fieldName);
        return v instanceof PrimitiveValue pv ? pv.intValue() : fallback;
    }
}
