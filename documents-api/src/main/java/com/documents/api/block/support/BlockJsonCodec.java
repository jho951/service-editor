package com.documents.api.block.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BlockJsonCodec {

    private final ObjectMapper objectMapper;

    public String write(JsonNode value) {
        if (value == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize block JSON field.", ex);
        }
    }

    public JsonNode read(String value) {
        if (value == null) {
            return null;
        }

        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException ex) {
            return TextNode.valueOf(value);
        }
    }
}
