package com.documents.api.document;

import static org.assertj.core.api.Assertions.assertThat;
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
@DisplayName("Document transaction API 통합 검증")
class DocumentTransactionApiIntegrationTest {

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
    @DisplayName("실패_create 뒤 replace_content가 충돌하면 전체 transaction을 rollback한다")
    void applyTransactionsRollsBackCreatedBlockWhenLaterOperationConflicts() throws Exception {
        Document document = document("문서");
        Block existingBlock = block(document, null, "기존 블록", "000000000001000000000000");

        existingBlock.setContent(content("다른 사용자 수정"));
        blockRepository.save(existingBlock);

        mockMvc.perform(post("/v1/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-1",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_CREATE",
                                      "blockRef": "tmp:block:1"
                                    },
                                    {
                                      "opId": "op-2",
                                      "type": "BLOCK_REPLACE_CONTENT",
                                      "blockRef": "%s",
                                      "version": 0,
                                      "content": {
                                        "format": "rich_text",
                                        "schemaVersion": 1,
                                        "segments": [
                                          {
                                            "text": "충돌 내용",
                                            "marks": []
                                          }
                                        ]
                                      }
                                    }
                                  ]
                                }
                                """.formatted(existingBlock.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.httpStatus").value("CONFLICT"))
                .andExpect(jsonPath("$.code").value(9005))
                .andExpect(jsonPath("$.message").value("요청이 현재 리소스 상태와 충돌합니다."));

        assertThat(blockRepository.countActiveByDocumentId(document.getId())).isEqualTo(1);
        assertThat(blockRepository.findActiveByDocumentIdOrderBySortKey(document.getId()))
                .extracting(Block::getId)
                .containsExactly(existingBlock.getId());
    }

    @Test
    @DisplayName("성공_existing block replace_content는 본문과 version을 갱신한다")
    void applyTransactionsReplacesExistingBlockContent() throws Exception {
        Document document = document("문서");
        Block existingBlock = block(document, null, "기존 블록", "000000000001000000000000");

        mockMvc.perform(post("/v1/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-1",
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
                                """.formatted(existingBlock.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appliedOperations[0].blockId").value(existingBlock.getId().toString()))
                .andExpect(jsonPath("$.data.appliedOperations[0].version").value(1));

        Block updatedBlock = blockRepository.findByIdAndDeletedAtIsNull(existingBlock.getId()).orElseThrow();
        assertThat(updatedBlock.getContent()).isEqualTo(content("수정된 블록"));
        assertThat(updatedBlock.getUpdatedBy()).isEqualTo("user-456");
        assertThat(updatedBlock.getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("실패_존재하지 않는 문서의 transaction 요청은 문서 없음 응답을 반환한다")
    void applyTransactionsReturnsNotFoundWhenDocumentMissing() throws Exception {
        mockMvc.perform(post("/v1/documents/{documentId}/transactions", UUID.randomUUID())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-1",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_CREATE",
                                      "blockRef": "tmp:block:1"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.httpStatus").value("NOT_FOUND"))
                .andExpect(jsonPath("$.code").value(9004))
                .andExpect(jsonPath("$.message").value("요청한 문서를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("실패_create의 parentId가 다른 문서 블록이면 잘못된 요청 응답을 반환한다")
    void applyTransactionsReturnsBadRequestWhenCreateParentBelongsToOtherDocument() throws Exception {
        Document targetDocument = document("대상 문서");
        Document otherDocument = document("다른 문서");
        Block otherDocumentBlock = block(otherDocument, null, "다른 문서 블록", "000000000001000000000000");

        mockMvc.perform(post("/v1/documents/{documentId}/transactions", targetDocument.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-1",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_CREATE",
                                      "blockRef": "tmp:block:1",
                                      "parentId": "%s"
                                    }
                                  ]
                                }
                                """.formatted(otherDocumentBlock.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.code").value(9015))
                .andExpect(jsonPath("$.message").value("잘못된 요청입니다."));
    }

    @Test
    @DisplayName("실패_create의 parentId가 존재하지 않으면 블록 없음 응답을 반환한다")
    void applyTransactionsReturnsNotFoundWhenCreateParentMissing() throws Exception {
        Document document = document("문서");

        mockMvc.perform(post("/v1/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-1",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_CREATE",
                                      "blockRef": "tmp:block:1",
                                      "parentId": "%s"
                                    }
                                  ]
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.httpStatus").value("NOT_FOUND"))
                .andExpect(jsonPath("$.code").value(9006))
                .andExpect(jsonPath("$.message").value("요청한 블록을 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("실패_create의 afterBlockId가 활성 sibling이 아니면 잘못된 요청 응답을 반환한다")
    void applyTransactionsReturnsBadRequestWhenCreateAfterBlockIdIsInvalid() throws Exception {
        Document document = document("문서");

        mockMvc.perform(post("/v1/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-1",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_CREATE",
                                      "blockRef": "tmp:block:1",
                                      "afterBlockId": "%s"
                                    }
                                  ]
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.code").value(9015))
                .andExpect(jsonPath("$.message").value("잘못된 요청입니다."));
    }

    @Test
    @DisplayName("실패_create의 afterBlockId와 beforeBlockId가 같은 값을 가리키면 잘못된 요청 응답을 반환한다")
    void applyTransactionsReturnsBadRequestWhenCreateAnchorsPointToSameBlock() throws Exception {
        Document document = document("문서");
        Block siblingBlock = block(document, null, "형제 블록", "000000000001000000000000");

        mockMvc.perform(post("/v1/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-1",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_CREATE",
                                      "blockRef": "tmp:block:1",
                                      "afterBlockId": "%s",
                                      "beforeBlockId": "%s"
                                    }
                                  ]
                                }
                                """.formatted(siblingBlock.getId(), siblingBlock.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.code").value(9015))
                .andExpect(jsonPath("$.message").value("잘못된 요청입니다."));
    }

    @Test
    @DisplayName("성공_create 후 replace_content를 두 번 적용하면 최종 본문과 version이 누적 갱신된다")
    void applyTransactionsAccumulatesVersionAcrossConsecutiveReplaceContentOperations() throws Exception {
        Document document = document("문서");

        mockMvc.perform(post("/v1/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-1",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_CREATE",
                                      "blockRef": "tmp:block:1"
                                    },
                                    {
                                      "opId": "op-2",
                                      "type": "BLOCK_REPLACE_CONTENT",
                                      "blockRef": "tmp:block:1",
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
                                      "opId": "op-3",
                                      "type": "BLOCK_REPLACE_CONTENT",
                                      "blockRef": "tmp:block:1",
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
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appliedOperations[0].version").value(0))
                .andExpect(jsonPath("$.data.appliedOperations[1].version").value(1))
                .andExpect(jsonPath("$.data.appliedOperations[2].version").value(2));

        assertThat(blockRepository.countActiveByDocumentId(document.getId())).isEqualTo(1);

        Block createdBlock = blockRepository.findActiveByDocumentIdOrderBySortKey(document.getId()).get(0);
        assertThat(createdBlock.getContent()).isEqualTo(content("두 번째 수정"));
        assertThat(createdBlock.getVersion()).isEqualTo(2);
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
