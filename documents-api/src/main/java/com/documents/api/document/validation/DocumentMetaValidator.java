package com.documents.api.document.validation;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DocumentMetaValidator implements ConstraintValidator<ValidDocumentMeta, JsonNode> {

    @Override
    public boolean isValid(JsonNode value, ConstraintValidatorContext context) {
        if (value == null || value.isNull()) {
            return true;
        }

        if (!value.isObject()) {
            return false;
        }

        JsonNode typeNode = value.get("type");
        JsonNode valueNode = value.get("value");
        return hasText(typeNode) && hasText(valueNode);
    }

    private boolean hasText(JsonNode node) {
        return node != null && node.isTextual() && !node.textValue().isBlank();
    }
}
