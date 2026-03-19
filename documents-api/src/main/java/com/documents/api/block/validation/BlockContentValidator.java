package com.documents.api.block.validation;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Set;
import java.util.regex.Pattern;

public class BlockContentValidator implements ConstraintValidator<ValidBlockContent, JsonNode> {

    private static final String RICH_TEXT_FORMAT = "rich_text";
    private static final int SUPPORTED_SCHEMA_VERSION = 1;
    private static final int MAX_TEXT_LENGTH = 10000;
    private static final String TEXT_COLOR = "textColor";
    private static final Set<String> SUPPORTED_MARK_TYPES = Set.of(
            "bold",
            "italic",
            TEXT_COLOR,
            "underline",
            "strikethrough"
    );
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    @Override
    public boolean isValid(JsonNode value, ConstraintValidatorContext context) {
        if (value == null || value.isNull() || !value.isObject()) {
            return false;
        }

        JsonNode formatNode = value.get("format");
        JsonNode schemaVersionNode = value.get("schemaVersion");
        JsonNode segmentsNode = value.get("segments");

        if (!hasText(formatNode) || !RICH_TEXT_FORMAT.equals(formatNode.textValue())) {
            return false;
        }
        if (schemaVersionNode == null || !schemaVersionNode.isInt()
                || schemaVersionNode.intValue() != SUPPORTED_SCHEMA_VERSION) {
            return false;
        }
        if (segmentsNode == null || !segmentsNode.isArray() || segmentsNode.isEmpty()) {
            return false;
        }

        int totalTextLength = 0;
        for (JsonNode segmentNode : segmentsNode) {
            if (!segmentNode.isObject()) {
                return false;
            }

            JsonNode textNode = segmentNode.get("text");
            JsonNode marksNode = segmentNode.get("marks");

            if (textNode == null || !textNode.isTextual()) {
                return false;
            }
            if (marksNode == null || !marksNode.isArray()) {
                return false;
            }

            totalTextLength += textNode.textValue().length();
            if (totalTextLength > MAX_TEXT_LENGTH) {
                return false;
            }

            for (JsonNode markNode : marksNode) {
                if (!markNode.isObject()) {
                    return false;
                }

                JsonNode typeNode = markNode.get("type");
                if (!hasText(typeNode) || !SUPPORTED_MARK_TYPES.contains(typeNode.textValue())) {
                    return false;
                }

                if (TEXT_COLOR.equals(typeNode.textValue())) {
                    JsonNode valueNode = markNode.get("value");
                    if (!hasText(valueNode) || !HEX_COLOR_PATTERN.matcher(valueNode.textValue()).matches()) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private boolean hasText(JsonNode node) {
        return node != null && node.isTextual() && !node.textValue().isBlank();
    }
}
