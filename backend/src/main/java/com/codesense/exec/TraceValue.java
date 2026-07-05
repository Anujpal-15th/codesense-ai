package com.codesense.exec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "valueKind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TraceValue.Primitive.class, name = "primitive"),
        @JsonSubTypes.Type(value = TraceValue.StringVal.class, name = "string"),
        @JsonSubTypes.Type(value = TraceValue.NullVal.class, name = "null"),
        @JsonSubTypes.Type(value = TraceValue.ArraySummary.class, name = "array"),
        @JsonSubTypes.Type(value = TraceValue.ObjectSummary.class, name = "object"),
        @JsonSubTypes.Type(value = TraceValue.MapSummary.class, name = "map"),
        @JsonSubTypes.Type(value = TraceValue.SetSummary.class, name = "set"),
        @JsonSubTypes.Type(value = TraceValue.ListSummary.class, name = "list"),
        @JsonSubTypes.Type(value = TraceValue.Truncated.class, name = "truncated")
})
public sealed interface TraceValue
        permits TraceValue.Primitive, TraceValue.StringVal, TraceValue.NullVal,
        TraceValue.ArraySummary, TraceValue.ObjectSummary,
        TraceValue.MapSummary, TraceValue.SetSummary, TraceValue.ListSummary,
        TraceValue.Truncated {

    record Primitive(String primitiveType, String literal) implements TraceValue {
    }

    record StringVal(String value, boolean truncated) implements TraceValue {
    }

    record NullVal() implements TraceValue {
    }

    record ArraySummary(String componentType, int length, List<TraceValue> elements, boolean truncated) implements TraceValue {
    }

    record ObjectSummary(String type, String identityHash, List<VariableSnapshot> fields, boolean truncated) implements TraceValue {
    }

    /**
     * Semantic view of a {@code java.util.Map}: real key -> value entries,
     * produced by invoking {@code entrySet().toArray()} on the debuggee rather
     * than dumping the map's raw internal fields. {@code identityHash} lets the
     * frontend diff the same map across steps; {@code size} is the map's true
     * size (may exceed {@code entries.size()} when truncated).
     */
    record MapSummary(String type, String identityHash, int size, List<Entry> entries, boolean truncated) implements TraceValue {
        record Entry(TraceValue key, TraceValue value) {
        }
    }

    /** Semantic view of a {@code java.util.Set}: real elements. */
    record SetSummary(String type, String identityHash, int size, List<TraceValue> elements, boolean truncated) implements TraceValue {
    }

    /** Semantic view of a {@code java.util.List}: real elements in order. */
    record ListSummary(String type, String identityHash, int size, List<TraceValue> elements, boolean truncated) implements TraceValue {
    }

    record Truncated(String reason) implements TraceValue {
    }
}
