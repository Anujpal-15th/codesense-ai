package com.codesense.analysis;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Smaller local LLMs occasionally return a non-array shape (e.g. the string "{}")
 * for a field the prompt specifies as a JSON array of strings — observed with
 * llama3.2:1b returning "edgeCases": "{}" instead of []. Rather than letting the
 * whole analysis fail on one malformed field, treat anything that isn't a real
 * JSON array as "no items" — never fabricates content, just degrades gracefully,
 * consistent with how missing/null scalar fields already degrade elsewhere.
 */
final class LenientStringListDeserializer extends JsonDeserializer<List<String>> {

    @Override
    public List<String> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        if (!node.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual()) {
                result.add(item.asText());
            } else if (!item.isNull()) {
                result.add(item.toString());
            }
        }
        return result;
    }
}
