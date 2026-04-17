package com.documents.api.document.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("문서 JSON codec 검증")
class DocumentJsonCodecTest {

    private final DocumentJsonCodec documentJsonCodec = new DocumentJsonCodec(new ObjectMapper());

    @Test
    @DisplayName("성공_JSON 객체를 문자열로 직렬화한다")
    void writeSerializesJsonNode() throws Exception {
        var jsonNode = new ObjectMapper().readTree("""
                {
                  "type": "emoji",
                  "value": "📄"
                }
                """);

        assertThat(documentJsonCodec.write(jsonNode)).isEqualTo("{\"type\":\"emoji\",\"value\":\"📄\"}");
    }

    @Test
    @DisplayName("성공_JSON 문자열을 객체로 역직렬화한다")
    void readDeserializesJsonString() {
        var jsonNode = documentJsonCodec.read("{\"type\":\"image\",\"value\":\"cover-1\"}");

        assertThat(jsonNode.get("type").asText()).isEqualTo("image");
        assertThat(jsonNode.get("value").asText()).isEqualTo("cover-1");
    }
}
