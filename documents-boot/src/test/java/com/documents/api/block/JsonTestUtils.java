package com.documents.api.block;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonTestUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonTestUtils() {
    }

    public static String readString(String json, String pointerExpression) throws Exception {
        JsonNode root = OBJECT_MAPPER.readTree(json);
        String jsonPointer = pointerExpression.replace("$", "").replace(".", "/");
        return root.at(jsonPointer.startsWith("/") ? jsonPointer : "/" + jsonPointer).asText();
    }
}
