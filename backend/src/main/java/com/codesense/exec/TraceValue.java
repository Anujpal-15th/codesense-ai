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
        @JsonSubTypes.Type(value = TraceValue.Truncated.class, name = "truncated")
})
public sealed interface TraceValue
        permits TraceValue.Primitive, TraceValue.StringVal, TraceValue.NullVal,
        TraceValue.ArraySummary, TraceValue.ObjectSummary, TraceValue.Truncated {

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

    record Truncated(String reason) implements TraceValue {
    }
}
