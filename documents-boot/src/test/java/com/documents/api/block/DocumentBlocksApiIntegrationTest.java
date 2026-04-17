package com.documents.api.block;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.documents.domain.Block;
import com.documents.domain.BlockType;
import com.documents.domain.Document;
import com.documents.repository.BlockRepository;
import com.documents.repository.DocumentRepository;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Document blocks API 통합 검증")
class DocumentBlocksApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private BlockRepository blockRepository;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .defaultRequest(get("/").header("X-User-Id", "user-123"))
                .build();
        blockRepository.deleteAll();
        documentRepository.deleteAll();
    }

    @Test
    @DisplayName("성공_문서 블록 목록 조회 API는 활성 블록 전체를 정렬 순서대로 반환한다")
    void getBlocksReturnsAllActiveBlocks() throws Exception {
        Document document = document("문서");
        Block rootBlock = block(document, null, "루트 블록", "000000000001000000000000");
        block(document, rootBlock, "자식 블록", "000000000001I00000000000");
        blockRepository.save(Block.builder()
                .id(UUID.randomUUID())
                .document(document)
                .type(BlockType.TEXT)
                .content(content("삭제된 블록"))
                .sortKey("000000000002000000000000")
                .createdBy("user-123")
                .updatedBy("user-123")
                .deletedAt(LocalDateTime.of(2026, 3, 16, 0, 0))
                .build());

        mockMvc.perform(get("/documents/{documentId}/blocks", document.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].content.segments[0].text").value("루트 블록"))
                .andExpect(jsonPath("$.data[1].content.segments[0].text").value("자식 블록"));
    }

    private Document document(String title) {
        return documentRepository.save(Document.builder()
                .id(UUID.randomUUID())
                .title(title)
                .sortKey("00000000000000000001")
                .createdBy("user-123")
                .updatedBy("user-123")
                .build());
    }

    private Block block(Document document, Block parent, String text, String sortKey) {
        return blockRepository.save(Block.builder()
                .id(UUID.randomUUID())
                .document(document)
                .parent(parent)
                .type(BlockType.TEXT)
                .content(content(text))
                .sortKey(sortKey)
                .createdBy("user-123")
                .updatedBy("user-123")
                .build());
    }

    private String content(String text) {
        return "{\"format\":\"rich_text\",\"schemaVersion\":1,\"segments\":[{\"text\":\"%s\",\"marks\":[]}]}".formatted(text);
    }
}
