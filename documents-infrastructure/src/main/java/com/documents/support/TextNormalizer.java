package com.documents.support;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TextNormalizer {

    public String normalizeRequired(String value) {
        return value.trim();
    }

    public String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
