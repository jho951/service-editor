package com.documents.api.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.documents.domain.Block;
import com.documents.domain.BlockType;
import com.documents.domain.Document;
import com.documents.domain.Workspace;
import com.documents.repository.BlockRepository;
import com.documents.repository.DocumentRepository;
import com.documents.repository.WorkspaceRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Block API 통합 검증")
class BlockApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private BlockRepository blockRepository;

    @BeforeEach
    void setUp() {
        blockRepository.deleteAll();
        documentRepository.deleteAll();
        workspaceRepository.deleteAll();
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

        mockMvc.perform(get("/v1/documents/{documentId}/blocks", document.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].content.segments[0].text").value("루트 블록"))
                .andExpect(jsonPath("$.data[1].content.segments[0].text").value("자식 블록"));
    }

    @Test
    @DisplayName("성공_생성 admin API는 transaction create 응답을 그대로 반환한다")
    void createBlockUsesTransactionContract() throws Exception {
        Document document = document("문서");

        mockMvc.perform(post("/v1/admin/documents/{documentId}/blocks", document.getId())
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
    @DisplayName("성공_수정 admin API는 transaction replace_content 경로로 같은 블록을 수정한다")
    void updateBlockUsesTransactionContract() throws Exception {
        Document document = document("문서");
        Block block = block(document, null, "기존 블록", "000000000001000000000000");

        mockMvc.perform(patch("/v1/admin/blocks/{blockId}", block.getId())
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
    @DisplayName("성공_이동 admin API는 transaction move 경로로 같은 블록을 이동한다")
    void moveBlockUsesTransactionContract() throws Exception {
        Document document = document("문서");
        Block targetParent = block(document, null, "부모 블록", "000000000001000000000000");
        Block moved = block(document, null, "이동 블록", "000000000002000000000000");

        mockMvc.perform(post("/v1/admin/blocks/{blockId}/move", moved.getId())
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
    @DisplayName("성공_삭제 admin API는 transaction delete 경로로 subtree soft delete를 수행한다")
    void deleteBlockUsesTransactionContract() throws Exception {
        Document document = document("문서");
        Block root = block(document, null, "루트 블록", "000000000001000000000000");
        Block child = block(document, root, "자식 블록", "000000000001I00000000000");

        mockMvc.perform(delete("/v1/admin/blocks/{blockId}", root.getId())
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

        mockMvc.perform(patch("/v1/admin/blocks/{blockId}", block.getId())
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

    private Document document(String title) {
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .id(UUID.randomUUID())
                .name("Docs Root")
                .build());
        return documentRepository.save(Document.builder()
                .id(UUID.randomUUID())
                .workspace(workspace)
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
