package com.documents.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("문자열 정규화 유틸 검증")
class TextNormalizerTest {

    private final TextNormalizer textNormalizer = new TextNormalizer();

    @Test
    @DisplayName("성공_필수 문자열 정규화는 앞뒤 공백을 제거한다")
    void normalizeRequiredTrimsWhitespace() {
        assertThat(textNormalizer.normalizeRequired("  프로젝트 개요  ")).isEqualTo("프로젝트 개요");
    }

    @Test
    @DisplayName("성공_nullable 문자열 정규화는 공백만 있는 값을 null로 변환한다")
    void normalizeNullableReturnsNullWhenBlank() {
        assertThat(textNormalizer.normalizeNullable("   ")).isNull();
    }
}
