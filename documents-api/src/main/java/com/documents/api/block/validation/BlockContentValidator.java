package com.documents.api.block.validation;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class BlockContentValidator implements ConstraintValidator<ValidBlockContent, JsonNode> {

    private static final String RICH_TEXT_FORMAT = "rich_text";
    private static final int SUPPORTED_SCHEMA_VERSION = 1;
    private static final int MAX_TEXT_LENGTH = 10000;
    private static final String TEXT_COLOR = "textColor";
    private static final Set<String> SUPPORTED_BLOCK_TYPES = Set.of(
            "paragraph",
            "heading1",
            "heading2",
            "heading3"
    );
    private static final Set<String> SUPPORTED_MARK_TYPES = Set.of(
            "bold",
            "italic",
            TEXT_COLOR,
            "underline",
            "strikethrough"
    );
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final Set<String> ALLOWED_CONTENT_FIELDS = Set.of("format", "schemaVersion", "segments", "blockType");
    private static final Set<String> ALLOWED_SEGMENT_FIELDS = Set.of("text", "marks");
    private static final Set<String> ALLOWED_MARK_FIELDS = Set.of("type", "value");

    @Override
    public boolean isValid(JsonNode value, ConstraintValidatorContext context) {
        if (value == null || value.isNull()) {
            return true;
        }
        if (!value.isObject()) {
            return false;
        }
        if (!containsOnlyAllowedFields(value, ALLOWED_CONTENT_FIELDS)) {
            return false;
        }

        JsonNode formatNode = value.get("format");
        JsonNode schemaVersionNode = value.get("schemaVersion");
        JsonNode segmentsNode = value.get("segments");
        JsonNode blockTypeNode = value.get("blockType");

        if (!hasText(formatNode) || !RICH_TEXT_FORMAT.equals(formatNode.textValue())) {
            return false;
        }
        if (schemaVersionNode == null || !schemaVersionNode.isInt()
                || schemaVersionNode.intValue() != SUPPORTED_SCHEMA_VERSION) {
            return false;
        }
        if (blockTypeNode != null && (!hasText(blockTypeNode) || !SUPPORTED_BLOCK_TYPES.contains(blockTypeNode.textValue()))) {
            return false;
        }
        if (segmentsNode == null || !segmentsNode.isArray() || segmentsNode.isEmpty()) {
            return false;
        }

        if (isAllowedEmptyBlock(segmentsNode)) {
            return true;
        }

        int totalTextLength = 0;
        for (JsonNode segmentNode : segmentsNode) {
            if (!segmentNode.isObject()) {
                return false;
            }
            if (!containsOnlyAllowedFields(segmentNode, ALLOWED_SEGMENT_FIELDS)) {
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
            if (textNode.textValue().isEmpty()) {
                return false;
            }

            totalTextLength += textNode.textValue().length();
            if (totalTextLength > MAX_TEXT_LENGTH) {
                return false;
            }

            Set<String> usedMarkTypes = new HashSet<>();
            for (JsonNode markNode : marksNode) {
                if (!markNode.isObject()) {
                    return false;
                }
                if (!containsOnlyAllowedFields(markNode, ALLOWED_MARK_FIELDS)) {
                    return false;
                }

                JsonNode typeNode = markNode.get("type");
                if (!hasText(typeNode) || !SUPPORTED_MARK_TYPES.contains(typeNode.textValue())) {
                    return false;
                }
                if (!usedMarkTypes.add(typeNode.textValue())) {
                    return false;
                }

                if (TEXT_COLOR.equals(typeNode.textValue())) {
                    JsonNode valueNode = markNode.get("value");
                    if (!hasText(valueNode) || !HEX_COLOR_PATTERN.matcher(valueNode.textValue()).matches()) {
                        return false;
                    }
                } else if (markNode.has("value")) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean isAllowedEmptyBlock(JsonNode segmentsNode) {
        if (segmentsNode.size() != 1) {
            return false;
        }

        JsonNode segmentNode = segmentsNode.get(0);
        if (segmentNode == null || !segmentNode.isObject()) {
            return false;
        }
        if (!containsOnlyAllowedFields(segmentNode, ALLOWED_SEGMENT_FIELDS)) {
            return false;
        }

        JsonNode textNode = segmentNode.get("text");
        JsonNode marksNode = segmentNode.get("marks");
        return textNode != null
                && textNode.isTextual()
                && textNode.textValue().isEmpty()
                && marksNode != null
                && marksNode.isArray()
                && marksNode.isEmpty();
    }

    private boolean containsOnlyAllowedFields(JsonNode objectNode, Set<String> allowedFields) {
        var fieldNames = objectNode.fieldNames();
        while (fieldNames.hasNext()) {
            if (!allowedFields.contains(fieldNames.next())) {
                return false;
            }
        }
        return true;
    }

    private boolean hasText(JsonNode node) {
        return node != null && node.isTextual() && !node.textValue().isBlank();
    }
}
