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
 */
class TraceValueConverter {

    private final TraceLimits limits;

    TraceValueConverter(TraceLimits limits) {
        this.limits = limits;
    }

    TraceValue convert(Value value) {
        return convert(value, 0, new HashSet<>());
    }

    private TraceValue convert(Value value, int depth, Set<Long> inProgress) {
        if (value == null) {
            return new TraceValue.NullVal();
        }
        if (depth > limits.maxObjectDepth()) {
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
            return convertArray(ar, depth, inProgress);
        }
        if (value instanceof ObjectReference or) {
            return convertObject(or, depth, inProgress);
        }
        return new TraceValue.Truncated("unsupported-value-type:" + value.type().name());
    }

    private TraceValue convertArray(ArrayReference ar, int depth, Set<Long> inProgress) {
        int length = ar.length();
        int limit = Math.min(length, limits.maxArrayElements());
        List<Value> values = ar.getValues(0, limit);
        List<TraceValue> elements = new ArrayList<>();
        for (Value v : values) {
            elements.add(convert(v, depth + 1, inProgress));
        }
        String componentType = ar.referenceType() instanceof ArrayType at
                ? at.componentTypeName()
                : "unknown";
        return new TraceValue.ArraySummary(componentType, length, elements, length > limit);
    }

    private TraceValue convertObject(ObjectReference or, int depth, Set<Long> inProgress) {
        long id = or.uniqueID();
        if (inProgress.contains(id)) {
            return new TraceValue.ObjectSummary(or.referenceType().name(), String.valueOf(id), List.of(), true);
        }
        inProgress.add(id);
        try {
            List<Field> instanceFields = or.referenceType().allFields().stream()
                    .filter(f -> !f.isStatic())
                    .toList();
            int limit = Math.min(instanceFields.size(), limits.maxObjectFields());
            List<VariableSnapshot> fields = new ArrayList<>();
            for (int i = 0; i < limit; i++) {
                Field field = instanceFields.get(i);
                Value fieldValue = or.getValue(field);
                fields.add(new VariableSnapshot(field.name(), field.typeName(), convert(fieldValue, depth + 1, inProgress)));
            }
            return new TraceValue.ObjectSummary(or.referenceType().name(), String.valueOf(id), fields, instanceFields.size() > limit);
        } finally {
            inProgress.remove(id);
        }
    }
}
