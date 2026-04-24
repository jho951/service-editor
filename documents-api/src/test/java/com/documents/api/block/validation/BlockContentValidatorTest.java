package com.documents.api.block.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BlockContentValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BlockContentValidator validator = new BlockContentValidator();

    @Test
    @DisplayName("rich_text content는 heading blockType을 허용한다")
    void acceptsHeadingBlockType() throws Exception {
        var content = objectMapper.readTree("""
                {
                  "format": "rich_text",
                  "schemaVersion": 1,
                  "blockType": "heading1",
                  "segments": [
                    {
                      "text": "제목",
                      "marks": []
                    }
                  ]
                }
                """);

        assertThat(validator.isValid(content, null)).isTrue();
    }

    @Test
    @DisplayName("rich_text content는 blockType이 없으면 paragraph로 간주한다")
    void acceptsContentWithoutBlockType() throws Exception {
        var content = objectMapper.readTree("""
                {
                  "format": "rich_text",
                  "schemaVersion": 1,
                  "segments": [
                    {
                      "text": "본문",
                      "marks": []
                    }
                  ]
                }
                """);

        assertThat(validator.isValid(content, null)).isTrue();
    }

    @Test
    @DisplayName("지원하지 않는 blockType은 거절한다")
    void rejectsUnsupportedBlockType() throws Exception {
        var content = objectMapper.readTree("""
                {
                  "format": "rich_text",
                  "schemaVersion": 1,
                  "blockType": "code_block",
                  "segments": [
                    {
                      "text": "본문",
                      "marks": []
                    }
                  ]
                }
                """);

        assertThat(validator.isValid(content, null)).isFalse();
    }
}
