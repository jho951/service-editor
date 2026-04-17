package com.documents.api.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.documents.domain.Block;
import com.documents.domain.BlockType;
import com.documents.domain.Document;
import com.documents.repository.BlockRepository;
import com.documents.repository.DocumentRepository;
import java.util.List;
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

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("AdminBlock API 통합 검증")
class AdminBlockApiIntegrationTest {

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
                .defaultRequest(post("/").header("X-User-Id", "user-123"))
                .build();
        blockRepository.deleteAll();
        documentRepository.deleteAll();
    }

    @Test
    @DisplayName("성공_생성 admin API는 editor save create 응답을 그대로 반환한다")
    void createBlockUsesEditorSaveContract() throws Exception {
        Document document = document("문서");

        mockMvc.perform(post("/admin/documents/{documentId}/blocks", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "clientId": "admin-api",
                                  "batchId": "batch-create",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_CREATE",
                                      "blockRef": "tmp:block:1"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentId").value(document.getId().toString()))
                .andExpect(jsonPath("$.data.batchId").value("batch-create"))
                .andExpect(jsonPath("$.data.appliedOperations[0].tempId").value("tmp:block:1"))
                .andExpect(jsonPath("$.data.appliedOperations[0].status").value("APPLIED"));

        List<Block> blocks = blockRepository.findActiveByDocumentIdOrderBySortKey(document.getId());
        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).getContent()).isEqualTo(emptyContent());
    }

    @Test
    @DisplayName("성공_수정 admin API는 editor save replace_content 경로로 같은 블록을 수정한다")
    void updateBlockUsesEditorSaveContract() throws Exception {
        Document document = document("문서");
        Block block = block(document, null, "기존 블록", "000000000001000000000000");

        mockMvc.perform(patch("/admin/blocks/{blockId}", block.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "clientId": "admin-api",
                                  "batchId": "batch-update",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_REPLACE_CONTENT",
                                      "blockRef": "%s",
                                      "version": 0,
                                      "content": {
                                        "format": "rich_text",
                                        "schemaVersion": 1,
                                        "segments": [
                                          {
                                            "text": "수정된 블록",
                                            "marks": []
                                          }
                                        ]
                                      }
                                    }
                                  ]
                                }
                                """.formatted(block.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentId").value(document.getId().toString()))
                .andExpect(jsonPath("$.data.appliedOperations[0].blockId").value(block.getId().toString()))
                .andExpect(jsonPath("$.data.appliedOperations[0].version").value(1))
                .andExpect(jsonPath("$.data.appliedOperations[0].status").value("APPLIED"));

        Block updated = blockRepository.findByIdAndDeletedAtIsNull(block.getId()).orElseThrow();
        assertThat(updated.getContent()).isEqualTo(content("수정된 블록"));
        assertThat(updated.getUpdatedBy()).isEqualTo("user-456");
    }

    @Test
    @DisplayName("성공_이동 admin API는 editor save move 경로로 같은 블록을 이동한다")
    void moveBlockUsesEditorSaveContract() throws Exception {
        Document document = document("문서");
        Block targetParent = block(document, null, "부모 블록", "000000000001000000000000");
        Block moved = block(document, null, "이동 블록", "000000000002000000000000");

        mockMvc.perform(post("/admin/blocks/{blockId}/move", moved.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "clientId": "admin-api",
                                  "batchId": "batch-move",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_MOVE",
                                      "blockRef": "%s",
                                      "version": 0,
                                      "parentRef": "%s"
                                    }
                                  ]
                                }
                                """.formatted(moved.getId(), targetParent.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appliedOperations[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.data.appliedOperations[0].blockId").value(moved.getId().toString()));

        Block reloaded = blockRepository.findByIdAndDeletedAtIsNull(moved.getId()).orElseThrow();
        assertThat(reloaded.getParentId()).isEqualTo(targetParent.getId());
    }

    @Test
    @DisplayName("성공_삭제 admin API는 editor save delete 경로로 subtree soft delete를 수행한다")
    void deleteBlockUsesEditorSaveContract() throws Exception {
        Document document = document("문서");
        Block root = block(document, null, "루트 블록", "000000000001000000000000");
        Block child = block(document, root, "자식 블록", "000000000001I00000000000");

        mockMvc.perform(delete("/admin/blocks/{blockId}", root.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "clientId": "admin-api",
                                  "batchId": "batch-delete",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_DELETE",
                                      "blockRef": "%s",
                                      "version": 0
                                    }
                                  ]
                                }
                                """.formatted(root.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appliedOperations[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.data.appliedOperations[0].blockId").value(root.getId().toString()))
                .andExpect(jsonPath("$.data.appliedOperations[0].deletedAt").exists());

        assertThat(blockRepository.findByIdAndDeletedAtIsNull(root.getId())).isEmpty();
        assertThat(blockRepository.findByIdAndDeletedAtIsNull(child.getId())).isEmpty();
    }

    @Test
    @DisplayName("실패_path blockId와 body blockRef가 다르면 잘못된 요청을 반환한다")
    void updateBlockRejectsMismatchedBlockReference() throws Exception {
        Block block = block(document("문서"), null, "기존 블록", "000000000001000000000000");

        mockMvc.perform(patch("/admin/blocks/{blockId}", block.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "clientId": "admin-api",
                                  "batchId": "batch-invalid",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_REPLACE_CONTENT",
                                      "blockRef": "%s",
                                      "version": 0,
                                      "content": {
                                        "format": "rich_text",
                                        "schemaVersion": 1,
                                        "segments": [
                                          {
                                            "text": "수정",
                                            "marks": []
                                          }
                                        ]
                                      }
                                    }
                                  ]
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.code").value(9015));
    }

    @Test
    @DisplayName("실패_없는 block이면 body blockRef가 틀려도 block not found를 우선 반환한다")
    void updateBlockReturnsBlockNotFoundBeforeBlockReferenceValidation() throws Exception {
        UUID missingBlockId = UUID.randomUUID();

        mockMvc.perform(patch("/admin/blocks/{blockId}", missingBlockId)
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "clientId": "admin-api",
                                  "batchId": "batch-missing",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_REPLACE_CONTENT",
                                      "blockRef": "%s",
                                      "version": 0,
                                      "content": {
                                        "format": "rich_text",
                                        "schemaVersion": 1,
                                        "segments": [
                                          {
                                            "text": "수정",
                                            "marks": []
                                          }
                                        ]
                                      }
                                    }
                                  ]
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.httpStatus").value("NOT_FOUND"))
                .andExpect(jsonPath("$.code").value(9006));
    }

    @Test
    @DisplayName("실패_여러 operation이 오면 단건 보조 API 계약 위반으로 거부한다")
    void updateBlockRejectsMultipleOperations() throws Exception {
        Document document = document("문서");
        Block block = block(document, null, "기존 블록", "000000000001000000000000");

        mockMvc.perform(patch("/admin/blocks/{blockId}", block.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "clientId": "admin-api",
                                  "batchId": "batch-multi",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_REPLACE_CONTENT",
                                      "blockRef": "%s",
                                      "version": 0,
                                      "content": {
                                        "format": "rich_text",
                                        "schemaVersion": 1,
                                        "segments": [
                                          {
                                            "text": "첫 번째 수정",
                                            "marks": []
                                          }
                                        ]
                                      }
                                    },
                                    {
                                      "opId": "op-2",
                                      "type": "BLOCK_REPLACE_CONTENT",
                                      "blockRef": "%s",
                                      "version": 1,
                                      "content": {
                                        "format": "rich_text",
                                        "schemaVersion": 1,
                                        "segments": [
                                          {
                                            "text": "두 번째 수정",
                                            "marks": []
                                          }
                                        ]
                                      }
                                    }
                                  ]
                                }
                                """.formatted(block.getId(), block.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.code").value(9015));

        Block updated = blockRepository.findByIdAndDeletedAtIsNull(block.getId()).orElseThrow();
        assertThat(updated.getContent()).isEqualTo(content("기존 블록"));
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

    private String emptyContent() {
        return "{\"format\":\"rich_text\",\"schemaVersion\":1,\"segments\":[{\"text\":\"\",\"marks\":[]}]}";
    }
}
