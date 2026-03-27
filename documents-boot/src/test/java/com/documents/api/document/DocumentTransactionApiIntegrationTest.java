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

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
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

    @DisplayName("성공_block_delete는 루트 블록과 하위 subtree를 soft delete 처리한다")
    void applyTransactionsDeletesBlockSubtree() throws Exception {
        Document document = document("문서");
        Block rootBlock = block(document, null, "루트 블록", "000000000001000000000000");
        Block childBlock = block(document, rootBlock, "자식 블록", "000000000001I00000000000");
        Block siblingBlock = block(document, null, "형제 블록", "000000000002000000000000");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "clientId": "web-editor",
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
                                """.formatted(rootBlock.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appliedOperations[0].blockId").value(rootBlock.getId().toString()))
                .andExpect(jsonPath("$.data.appliedOperations[0].deletedAt").exists());

        Block deletedRootBlock = blockRepository.findById(rootBlock.getId()).orElseThrow();
        Block deletedChildBlock = blockRepository.findById(childBlock.getId()).orElseThrow();
        Block activeSiblingBlock = blockRepository.findById(siblingBlock.getId()).orElseThrow();

        assertThat(deletedRootBlock.getDeletedAt()).isNotNull();
        assertThat(deletedChildBlock.getDeletedAt()).isNotNull();
        assertThat(activeSiblingBlock.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("성공_block_move는 기존 블록 위치를 옮기고 version과 sortKey를 갱신한다")
    void applyTransactionsMovesExistingBlock() throws Exception {
        Document document = document("문서");
        Block beforeBlock = block(document, null, "앞 블록", "000000000001000000000000");
        Block movingBlock = block(document, null, "이동 대상", "000000000002000000000000");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-move",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_MOVE",
                                      "blockRef": "%s",
                                      "version": 0,
                                      "parentRef": null,
                                      "afterRef": null,
                                      "beforeRef": "%s"
                                    }
                                  ]
                                }
                                """.formatted(movingBlock.getId(), beforeBlock.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appliedOperations[0].blockId").value(movingBlock.getId().toString()))
                .andExpect(jsonPath("$.data.appliedOperations[0].version").value(1))
                .andExpect(jsonPath("$.data.appliedOperations[0].sortKey").exists());

        Block reloadedMovingBlock = blockRepository.findByIdAndDeletedAtIsNull(movingBlock.getId()).orElseThrow();
        assertThat(reloadedMovingBlock.getVersion()).isEqualTo(1);
        assertThat(reloadedMovingBlock.getUpdatedBy()).isEqualTo("user-456");
        assertThat(reloadedMovingBlock.getSortKey()).isNotEqualTo("000000000002000000000000");
    }

    @Test
    @DisplayName("성공_block_move는 temp parentRef를 실제 parentId로 해석한다")
    void applyTransactionsMovesExistingBlockUnderTempParent() throws Exception {
        Document document = document("문서");
        Block movingBlock = block(document, null, "이동 대상", "000000000001000000000000");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-temp-move",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_CREATE",
                                      "blockRef": "tmp:parent"
                                    },
                                    {
                                      "opId": "op-2",
                                      "type": "BLOCK_MOVE",
                                      "blockRef": "%s",
                                      "version": 0,
                                      "parentRef": "tmp:parent"
                                    }
                                  ]
                                }
                                """.formatted(movingBlock.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appliedOperations[1].blockId").value(movingBlock.getId().toString()))
                .andExpect(jsonPath("$.data.appliedOperations[1].version").value(1));

        Block createdParentBlock = blockRepository.findActiveByDocumentIdAndParentIdOrderBySortKey(document.getId(), null)
                .stream()
                .filter(block -> !block.getId().equals(movingBlock.getId()))
                .findFirst()
                .orElseThrow();
        Block movedBlock = blockRepository.findByIdAndDeletedAtIsNull(movingBlock.getId()).orElseThrow();

        assertThat(movedBlock.getParentId()).isEqualTo(createdParentBlock.getId());
    }

    @Test
    @DisplayName("성공_create 뒤 temp block move 후 replace_content를 적용하면 위치와 version이 누적 갱신된다")
    void applyTransactionsUpdatesTempBlockContextAfterMove() throws Exception {
        Document document = document("문서");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-temp-move-replace",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_CREATE",
                                      "blockRef": "tmp:parent"
                                    },
                                    {
                                      "opId": "op-2",
                                      "type": "BLOCK_CREATE",
                                      "blockRef": "tmp:block:1"
                                    },
                                    {
                                      "opId": "op-3",
                                      "type": "BLOCK_MOVE",
                                      "blockRef": "tmp:block:1",
                                      "parentRef": "tmp:parent"
                                    },
                                    {
                                      "opId": "op-4",
                                      "type": "BLOCK_REPLACE_CONTENT",
                                      "blockRef": "tmp:block:1",
                                      "content": {
                                        "format": "rich_text",
                                        "schemaVersion": 1,
                                        "segments": [
                                          {
                                            "text": "moved and replaced",
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
                .andExpect(jsonPath("$.data.appliedOperations[1].version").value(0))
                .andExpect(jsonPath("$.data.appliedOperations[2].version").value(1))
                .andExpect(jsonPath("$.data.appliedOperations[3].version").value(2));

        assertThat(blockRepository.countActiveByDocumentId(document.getId())).isEqualTo(2);

        Block parentBlock = blockRepository.findActiveByDocumentIdAndParentIdOrderBySortKey(document.getId(), null).get(0);
        Block movedBlock = blockRepository.findActiveChildrenByParentIdOrderBySortKey(parentBlock.getId()).get(0);

        assertThat(movedBlock.getContent()).isEqualTo(content("moved and replaced"));
        assertThat(movedBlock.getVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("성공_block_move no-op 뒤 replace_content는 기존 version으로 후속 수정에 성공한다")
    void applyTransactionsReplacesContentAfterMoveNoOp() throws Exception {
        Document document = document("문서");
        Block existingBlock = block(document, null, "기존 블록", "000000000001000000000000");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-no-op-then-replace",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_MOVE",
                                      "blockRef": "%s",
                                      "version": 0
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
                                            "text": "no-op 뒤 수정",
                                            "marks": []
                                          }
                                        ]
                                      }
                                    }
                                  ]
                                }
                                """.formatted(existingBlock.getId(), existingBlock.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appliedOperations[0].status").value("NO_OP"))
                .andExpect(jsonPath("$.data.appliedOperations[0].version").value(0))
                .andExpect(jsonPath("$.data.appliedOperations[1].status").value("APPLIED"))
                .andExpect(jsonPath("$.data.appliedOperations[1].version").value(1));

        Block reloadedBlock = blockRepository.findByIdAndDeletedAtIsNull(existingBlock.getId()).orElseThrow();
        assertThat(reloadedBlock.getContent()).isEqualTo(content("no-op 뒤 수정"));
        assertThat(reloadedBlock.getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("성공_replace_content no-op 뒤 move는 같은 version으로 후속 이동에 성공한다")
    void applyTransactionsMovesBlockAfterReplaceContentNoOp() throws Exception {
        Document document = document("문서");
        Block existingBlock = block(document, null, "기존 블록", "000000000001000000000000");
        Block targetParent = block(document, null, "부모 블록", "000000000002000000000000");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-no-op-replace-then-move",
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
                                            "text": "기존 블록",
                                            "marks": []
                                          }
                                        ]
                                      }
                                    },
                                    {
                                      "opId": "op-2",
                                      "type": "BLOCK_MOVE",
                                      "blockRef": "%s",
                                      "version": 0,
                                      "parentRef": "%s"
                                    }
                                  ]
                                }
                                """.formatted(existingBlock.getId(), existingBlock.getId(), targetParent.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appliedOperations[0].status").value("NO_OP"))
                .andExpect(jsonPath("$.data.appliedOperations[0].version").value(0))
                .andExpect(jsonPath("$.data.appliedOperations[1].status").value("APPLIED"))
                .andExpect(jsonPath("$.data.appliedOperations[1].version").value(1));

        Block reloadedBlock = blockRepository.findByIdAndDeletedAtIsNull(existingBlock.getId()).orElseThrow();
        assertThat(reloadedBlock.getParentId()).isEqualTo(targetParent.getId());
        assertThat(reloadedBlock.getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("성공_create 뒤 replace, move, replace, move를 연속 적용하면 temp block version과 위치가 끝까지 누적 갱신된다")
    void applyTransactionsAccumulatesTempBlockStateAcrossMixedOperations() throws Exception {
        Document document = document("문서");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-mixed-sequence",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_CREATE",
                                      "blockRef": "tmp:parent"
                                    },
                                    {
                                      "opId": "op-2",
                                      "type": "BLOCK_CREATE",
                                      "blockRef": "tmp:block:1"
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
                                            "text": "first replace",
                                            "marks": []
                                          }
                                        ]
                                      }
                                    },
                                    {
                                      "opId": "op-4",
                                      "type": "BLOCK_MOVE",
                                      "blockRef": "tmp:block:1",
                                      "parentRef": "tmp:parent"
                                    },
                                    {
                                      "opId": "op-5",
                                      "type": "BLOCK_REPLACE_CONTENT",
                                      "blockRef": "tmp:block:1",
                                      "content": {
                                        "format": "rich_text",
                                        "schemaVersion": 1,
                                        "segments": [
                                          {
                                            "text": "second replace",
                                            "marks": []
                                          }
                                        ]
                                      }
                                    },
                                    {
                                      "opId": "op-6",
                                      "type": "BLOCK_MOVE",
                                      "blockRef": "tmp:block:1"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appliedOperations[0].version").value(0))
                .andExpect(jsonPath("$.data.appliedOperations[1].version").value(0))
                .andExpect(jsonPath("$.data.appliedOperations[2].version").value(1))
                .andExpect(jsonPath("$.data.appliedOperations[3].version").value(2))
                .andExpect(jsonPath("$.data.appliedOperations[4].version").value(3))
                .andExpect(jsonPath("$.data.appliedOperations[5].version").value(4));

        assertThat(blockRepository.countActiveByDocumentId(document.getId())).isEqualTo(2);

        Block movedBackToRootBlock = blockRepository.findActiveByDocumentIdAndParentIdOrderBySortKey(document.getId(), null)
                .stream()
                .filter(block -> content("second replace").equals(block.getContent()))
                .findFirst()
                .orElseThrow();

        assertThat(movedBackToRootBlock.getParentId()).isNull();
        assertThat(movedBackToRootBlock.getVersion()).isEqualTo(4);
    }

    @Test
    @DisplayName("실패_block_delete가 temp blockRef를 참조하면 전체 transaction을 rollback한다")
    void applyTransactionsRollsBackCreatedBlockWhenDeleteReferencesTempBlock() throws Exception {
        Document document = document("문서");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-delete-temp",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_CREATE",
                                      "blockRef": "tmp:block:1"
                                    },
                                    {
                                      "opId": "op-2",
                                      "type": "BLOCK_DELETE",
                                      "blockRef": "tmp:block:1",
                                      "version": 0
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.code").value(9015))
                .andExpect(jsonPath("$.message").value("잘못된 요청입니다."));

        assertThat(blockRepository.countActiveByDocumentId(document.getId())).isZero();
    }

    @Test
    @DisplayName("실패_block_delete 뒤 같은 block replace_content를 참조하면 전체 transaction을 rollback한다")
    void applyTransactionsRollsBackDeleteWhenLaterReplaceReferencesDeletedBlock() throws Exception {
        Document document = document("문서");
        Block existingBlock = block(document, null, "기존 블록", "000000000001000000000000");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-delete-then-replace",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_DELETE",
                                      "blockRef": "%s",
                                      "version": 0
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
                                            "text": "삭제 후 수정",
                                            "marks": []
                                          }
                                        ]
                                      }
                                    }
                                  ]
                                }
                                """.formatted(existingBlock.getId(), existingBlock.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.httpStatus").value("NOT_FOUND"))
                .andExpect(jsonPath("$.code").value(9006))
                .andExpect(jsonPath("$.message").value("요청한 블록을 찾을 수 없습니다."));

        Block reloadedBlock = blockRepository.findByIdAndDeletedAtIsNull(existingBlock.getId()).orElseThrow();
        assertThat(reloadedBlock.getDeletedAt()).isNull();
        assertThat(reloadedBlock.getContent()).isEqualTo(content("기존 블록"));
    }

    @Test
    @DisplayName("실패_block_delete 뒤 같은 block move를 참조하면 전체 transaction을 rollback한다")
    void applyTransactionsRollsBackDeleteWhenLaterMoveReferencesDeletedBlock() throws Exception {
        Document document = document("문서");
        Block existingBlock = block(document, null, "기존 블록", "000000000001000000000000");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-delete-then-move",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_DELETE",
                                      "blockRef": "%s",
                                      "version": 0
                                    },
                                    {
                                      "opId": "op-2",
                                      "type": "BLOCK_MOVE",
                                      "blockRef": "%s",
                                      "version": 0
                                    }
                                  ]
                                }
                                """.formatted(existingBlock.getId(), existingBlock.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.httpStatus").value("NOT_FOUND"))
                .andExpect(jsonPath("$.code").value(9006))
                .andExpect(jsonPath("$.message").value("요청한 블록을 찾을 수 없습니다."));

        Block reloadedBlock = blockRepository.findByIdAndDeletedAtIsNull(existingBlock.getId()).orElseThrow();
        assertThat(reloadedBlock.getDeletedAt()).isNull();
        assertThat(reloadedBlock.getContent()).isEqualTo(content("기존 블록"));
    }

    @Test
    @DisplayName("실패_subtree delete 뒤 자식 block replace_content를 참조하면 전체 transaction을 rollback한다")
    void applyTransactionsRollsBackDeleteWhenLaterReplaceReferencesDeletedChildBlock() throws Exception {
        Document document = document("문서");
        Block parentBlock = block(document, null, "부모 블록", "000000000001000000000000");
        Block childBlock = block(document, parentBlock, "자식 블록", "000000000001I00000000000");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-delete-child-then-replace",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_DELETE",
                                      "blockRef": "%s",
                                      "version": 0
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
                                            "text": "삭제된 자식 수정",
                                            "marks": []
                                          }
                                        ]
                                      }
                                    }
                                  ]
                                }
                                """.formatted(parentBlock.getId(), childBlock.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.httpStatus").value("NOT_FOUND"))
                .andExpect(jsonPath("$.code").value(9006))
                .andExpect(jsonPath("$.message").value("요청한 블록을 찾을 수 없습니다."));

        Block reloadedParent = blockRepository.findByIdAndDeletedAtIsNull(parentBlock.getId()).orElseThrow();
        Block reloadedChild = blockRepository.findByIdAndDeletedAtIsNull(childBlock.getId()).orElseThrow();

        assertThat(reloadedParent.getDeletedAt()).isNull();
        assertThat(reloadedChild.getDeletedAt()).isNull();
        assertThat(reloadedChild.getContent()).isEqualTo(content("자식 블록"));
    }

    @Test
    @DisplayName("성공_real block replace_content 뒤 같은 base version move는 서버 내부 최신 version으로 이어서 처리한다")
    void applyTransactionsUsesServerManagedVersionAfterReplaceContentOnSameRealBlock() throws Exception {
        Document document = document("문서");
        Block existingBlock = block(document, null, "기존 블록", "000000000001000000000000");
        Block targetParent = block(document, null, "부모 블록", "000000000002000000000000");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-stale-real-block",
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
                                            "text": "먼저 수정",
                                            "marks": []
                                          }
                                        ]
                                      }
                                    },
                                    {
                                      "opId": "op-2",
                                      "type": "BLOCK_MOVE",
                                      "blockRef": "%s",
                                      "version": 0,
                                      "parentRef": "%s"
                                    }
                                  ]
                                }
                                """.formatted(existingBlock.getId(), existingBlock.getId(), targetParent.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appliedOperations[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.data.appliedOperations[0].version").value(1))
                .andExpect(jsonPath("$.data.appliedOperations[1].status").value("APPLIED"))
                .andExpect(jsonPath("$.data.appliedOperations[1].version").value(2));

        Block reloadedBlock = blockRepository.findByIdAndDeletedAtIsNull(existingBlock.getId()).orElseThrow();
        assertThat(reloadedBlock.getContent()).isEqualTo(content("먼저 수정"));
        assertThat(reloadedBlock.getVersion()).isEqualTo(2);
        assertThat(reloadedBlock.getParentId()).isEqualTo(targetParent.getId());
    }

    @Test
    @DisplayName("성공_real block replace_content, move, replace_content는 같은 base version으로 이어서 처리한다")
    void applyTransactionsUsesServerManagedVersionAcrossReplaceMoveReplaceOnSameRealBlock() throws Exception {
        Document document = document("문서");
        Block existingBlock = block(document, null, "기존 블록", "000000000001000000000000");
        Block targetParent = block(document, null, "부모 블록", "000000000002000000000000");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-real-block-chain",
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
                                            "text": "먼저 수정",
                                            "marks": []
                                          }
                                        ]
                                      }
                                    },
                                    {
                                      "opId": "op-2",
                                      "type": "BLOCK_MOVE",
                                      "blockRef": "%s",
                                      "version": 0,
                                      "parentRef": "%s"
                                    },
                                    {
                                      "opId": "op-3",
                                      "type": "BLOCK_REPLACE_CONTENT",
                                      "blockRef": "%s",
                                      "version": 0,
                                      "content": {
                                        "format": "rich_text",
                                        "schemaVersion": 1,
                                        "segments": [
                                          {
                                            "text": "마지막 수정",
                                            "marks": []
                                          }
                                        ]
                                      }
                                    }
                                  ]
                                }
                                """.formatted(
                                existingBlock.getId(),
                                existingBlock.getId(),
                                targetParent.getId(),
                                existingBlock.getId()
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appliedOperations[0].version").value(1))
                .andExpect(jsonPath("$.data.appliedOperations[1].version").value(2))
                .andExpect(jsonPath("$.data.appliedOperations[2].version").value(3));

        Block reloadedBlock = blockRepository.findByIdAndDeletedAtIsNull(existingBlock.getId()).orElseThrow();
        assertThat(reloadedBlock.getContent()).isEqualTo(content("마지막 수정"));
        assertThat(reloadedBlock.getVersion()).isEqualTo(3);
        assertThat(reloadedBlock.getParentId()).isEqualTo(targetParent.getId());
    }

    @Test
    @DisplayName("성공_real block replace_content, move 뒤 delete는 같은 base version으로 이어서 처리한다")
    void applyTransactionsUsesServerManagedVersionForDeleteAfterReplaceAndMoveOnSameRealBlock() throws Exception {
        Document document = document("문서");
        Block existingBlock = block(document, null, "기존 블록", "000000000001000000000000");
        Block targetParent = block(document, null, "부모 블록", "000000000002000000000000");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-real-block-delete-chain",
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
                                            "text": "먼저 수정",
                                            "marks": []
                                          }
                                        ]
                                      }
                                    },
                                    {
                                      "opId": "op-2",
                                      "type": "BLOCK_MOVE",
                                      "blockRef": "%s",
                                      "version": 0,
                                      "parentRef": "%s"
                                    },
                                    {
                                      "opId": "op-3",
                                      "type": "BLOCK_DELETE",
                                      "blockRef": "%s",
                                      "version": 0
                                    }
                                  ]
                                }
                                """.formatted(
                                existingBlock.getId(),
                                existingBlock.getId(),
                                targetParent.getId(),
                                existingBlock.getId()
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appliedOperations[0].version").value(1))
                .andExpect(jsonPath("$.data.appliedOperations[1].version").value(2))
                .andExpect(jsonPath("$.data.appliedOperations[2].status").value("APPLIED"))
                .andExpect(jsonPath("$.data.appliedOperations[2].deletedAt").isNotEmpty());

        Block deletedBlock = blockRepository.findById(existingBlock.getId()).orElseThrow();
        assertThat(deletedBlock.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("실패_real block이 같은 batch 안에서 다른 base version을 섞어 보내면 전체 transaction을 rollback한다")
    void applyTransactionsRollsBackWhenRealBlockUsesDifferentBaseVersionInsideBatch() throws Exception {
        Document document = document("문서");
        Block existingBlock = block(document, null, "기존 블록", "000000000001000000000000");
        Block targetParent = block(document, null, "부모 블록", "000000000002000000000000");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-inconsistent-base-version",
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
                                            "text": "먼저 수정",
                                            "marks": []
                                          }
                                        ]
                                      }
                                    },
                                    {
                                      "opId": "op-2",
                                      "type": "BLOCK_MOVE",
                                      "blockRef": "%s",
                                      "version": 1,
                                      "parentRef": "%s"
                                    }
                                  ]
                                }
                                """.formatted(existingBlock.getId(), existingBlock.getId(), targetParent.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.httpStatus").value("CONFLICT"))
                .andExpect(jsonPath("$.code").value(9005))
                .andExpect(jsonPath("$.message").value("요청이 현재 리소스 상태와 충돌합니다."));

        Block reloadedBlock = blockRepository.findByIdAndDeletedAtIsNull(existingBlock.getId()).orElseThrow();
        assertThat(reloadedBlock.getContent()).isEqualTo(content("기존 블록"));
        assertThat(reloadedBlock.getVersion()).isEqualTo(0);
        assertThat(reloadedBlock.getParentId()).isNull();
    }

    @Test
    @DisplayName("실패_existing block replace_content에 version이 없으면 잘못된 요청 응답을 반환한다")
    void applyTransactionsReturnsBadRequestWhenExistingReplaceContentOmitsVersion() throws Exception {
        Document document = document("문서");
        Block existingBlock = block(document, null, "기존 블록", "000000000001000000000000");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-missing-version-replace",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_REPLACE_CONTENT",
                                      "blockRef": "%s",
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
                                """.formatted(existingBlock.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.code").value(9015));
    }

    @Test
    @DisplayName("실패_existing block move에 version이 없으면 잘못된 요청 응답을 반환한다")
    void applyTransactionsReturnsBadRequestWhenExistingMoveOmitsVersion() throws Exception {
        Document document = document("문서");
        Block existingBlock = block(document, null, "기존 블록", "000000000001000000000000");
        Block targetParent = block(document, null, "부모 블록", "000000000002000000000000");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-missing-version-move",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_MOVE",
                                      "blockRef": "%s",
                                      "parentRef": "%s"
                                    }
                                  ]
                                }
                                """.formatted(existingBlock.getId(), targetParent.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.code").value(9015));
    }

    @Test
    @DisplayName("성공_create 뒤 temp block delete는 version 없이 같은 batch에서 처리한다")
    void applyTransactionsDeletesCreatedTempBlockWithoutVersion() throws Exception {
        Document document = document("문서");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-temp-delete",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_CREATE",
                                      "blockRef": "tmp:block:1"
                                    },
                                    {
                                      "opId": "op-2",
                                      "type": "BLOCK_DELETE",
                                      "blockRef": "tmp:block:1"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appliedOperations[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.data.appliedOperations[1].status").value("APPLIED"))
                .andExpect(jsonPath("$.data.appliedOperations[1].deletedAt").isNotEmpty());

        assertThat(blockRepository.countActiveByDocumentId(document.getId())).isZero();
    }

    @Test
    @DisplayName("성공_create 뒤 replace_content 후 temp block delete는 currentVersion으로 같은 batch에서 처리한다")
    void applyTransactionsDeletesCreatedTempBlockAfterReplaceContent() throws Exception {
        Document document = document("문서");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-temp-replace-delete",
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
                                            "text": "생성 후 수정",
                                            "marks": []
                                          }
                                        ]
                                      }
                                    },
                                    {
                                      "opId": "op-3",
                                      "type": "BLOCK_DELETE",
                                      "blockRef": "tmp:block:1"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appliedOperations[1].version").value(1))
                .andExpect(jsonPath("$.data.appliedOperations[2].status").value("APPLIED"))
                .andExpect(jsonPath("$.data.appliedOperations[2].deletedAt").isNotEmpty());

        assertThat(blockRepository.countActiveByDocumentId(document.getId())).isZero();
    }

    @Test
    @DisplayName("성공_create 뒤 move 후 temp block delete는 currentVersion으로 같은 batch에서 처리한다")
    void applyTransactionsDeletesCreatedTempBlockAfterMove() throws Exception {
        Document document = document("문서");
        Block parentBlock = block(document, null, "부모 블록", "000000000001000000000000");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-temp-move-delete",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_CREATE",
                                      "blockRef": "tmp:block:1"
                                    },
                                    {
                                      "opId": "op-2",
                                      "type": "BLOCK_MOVE",
                                      "blockRef": "tmp:block:1",
                                      "parentRef": "%s"
                                    },
                                    {
                                      "opId": "op-3",
                                      "type": "BLOCK_DELETE",
                                      "blockRef": "tmp:block:1"
                                    }
                                  ]
                                }
                                """.formatted(parentBlock.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appliedOperations[1].version").value(1))
                .andExpect(jsonPath("$.data.appliedOperations[2].status").value("APPLIED"))
                .andExpect(jsonPath("$.data.appliedOperations[2].deletedAt").isNotEmpty());

        assertThat(blockRepository.countActiveByDocumentId(document.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("실패_temp block delete에 version이 오면 잘못된 요청 응답을 반환한다")
    void applyTransactionsReturnsBadRequestWhenTempDeleteIncludesVersion() throws Exception {
        Document document = document("문서");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-temp-delete-with-version",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_CREATE",
                                      "blockRef": "tmp:block:1"
                                    },
                                    {
                                      "opId": "op-2",
                                      "type": "BLOCK_DELETE",
                                      "blockRef": "tmp:block:1",
                                      "version": 0
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.code").value(9015));
    }

    @Test
    @DisplayName("실패_existing block delete에 version이 없으면 잘못된 요청 응답을 반환한다")
    void applyTransactionsReturnsBadRequestWhenExistingDeleteOmitsVersion() throws Exception {
        Document document = document("문서");
        Block existingBlock = block(document, null, "기존 블록", "000000000001000000000000");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-existing-delete-without-version",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_DELETE",
                                      "blockRef": "%s"
                                    }
                                  ]
                                }
                                """.formatted(existingBlock.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.code").value(9015));
    }

    @Test
    @DisplayName("실패_temp block delete 뒤 같은 temp replace_content를 참조하면 전체 transaction을 rollback한다")
    void applyTransactionsRollsBackWhenLaterReplaceReferencesDeletedTempBlock() throws Exception {
        Document document = document("문서");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-temp-delete-then-replace",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_CREATE",
                                      "blockRef": "tmp:block:1"
                                    },
                                    {
                                      "opId": "op-2",
                                      "type": "BLOCK_DELETE",
                                      "blockRef": "tmp:block:1"
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
                                            "text": "삭제 후 수정",
                                            "marks": []
                                          }
                                        ]
                                      }
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.httpStatus").value("NOT_FOUND"))
                .andExpect(jsonPath("$.code").value(9006));

        assertThat(blockRepository.countActiveByDocumentId(document.getId())).isZero();
    }

    @Test
    @DisplayName("실패_temp block delete 뒤 같은 temp move를 참조하면 전체 transaction을 rollback한다")
    void applyTransactionsRollsBackWhenLaterMoveReferencesDeletedTempBlock() throws Exception {
        Document document = document("문서");
        Block parentBlock = block(document, null, "부모 블록", "000000000001000000000000");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-temp-delete-then-move",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_CREATE",
                                      "blockRef": "tmp:block:1"
                                    },
                                    {
                                      "opId": "op-2",
                                      "type": "BLOCK_DELETE",
                                      "blockRef": "tmp:block:1"
                                    },
                                    {
                                      "opId": "op-3",
                                      "type": "BLOCK_MOVE",
                                      "blockRef": "tmp:block:1",
                                      "parentRef": "%s"
                                    }
                                  ]
                                }
                                """.formatted(parentBlock.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.httpStatus").value("NOT_FOUND"))
                .andExpect(jsonPath("$.code").value(9006));

        assertThat(blockRepository.countActiveByDocumentId(document.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("성공_replace_content no-op 뒤 real block delete는 증가하지 않은 version으로 처리한다")
    void applyTransactionsDeletesExistingBlockAfterReplaceContentNoOp() throws Exception {
        Document document = document("문서");
        Block existingBlock = block(document, null, "기존 블록", "000000000001000000000000");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-replace-no-op-delete",
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
                                            "text": "기존 블록",
                                            "marks": []
                                          }
                                        ]
                                      }
                                    },
                                    {
                                      "opId": "op-2",
                                      "type": "BLOCK_DELETE",
                                      "blockRef": "%s",
                                      "version": 0
                                    }
                                  ]
                                }
                                """.formatted(existingBlock.getId(), existingBlock.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appliedOperations[0].status").value("NO_OP"))
                .andExpect(jsonPath("$.data.appliedOperations[0].version").value(0))
                .andExpect(jsonPath("$.data.appliedOperations[1].status").value("APPLIED"))
                .andExpect(jsonPath("$.data.appliedOperations[1].deletedAt").isNotEmpty());

        Block deletedBlock = blockRepository.findById(existingBlock.getId()).orElseThrow();
        assertThat(deletedBlock.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("성공_move no-op 뒤 real block delete는 증가하지 않은 version으로 처리한다")
    void applyTransactionsDeletesExistingBlockAfterMoveNoOp() throws Exception {
        Document document = document("문서");
        Block existingBlock = block(document, null, "기존 블록", "000000000001000000000000");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-move-no-op-delete",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_MOVE",
                                      "blockRef": "%s",
                                      "version": 0
                                    },
                                    {
                                      "opId": "op-2",
                                      "type": "BLOCK_DELETE",
                                      "blockRef": "%s",
                                      "version": 0
                                    }
                                  ]
                                }
                                """.formatted(existingBlock.getId(), existingBlock.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appliedOperations[0].status").value("NO_OP"))
                .andExpect(jsonPath("$.data.appliedOperations[0].version").value(0))
                .andExpect(jsonPath("$.data.appliedOperations[1].status").value("APPLIED"))
                .andExpect(jsonPath("$.data.appliedOperations[1].deletedAt").isNotEmpty());

        Block deletedBlock = blockRepository.findById(existingBlock.getId()).orElseThrow();
        assertThat(deletedBlock.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("실패_create 뒤 block_delete가 충돌하면 앞선 생성까지 전체 transaction을 rollback한다")
    void applyTransactionsRollsBackCreatedBlockWhenLaterDeleteConflicts() throws Exception {
        Document document = document("문서");
        Block existingBlock = block(document, null, "기존 블록", "000000000001000000000000");

        existingBlock.setContent(content("다른 사용자 수정"));
        blockRepository.save(existingBlock);

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-delete-conflict",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_CREATE",
                                      "blockRef": "tmp:block:1"
                                    },
                                    {
                                      "opId": "op-2",
                                      "type": "BLOCK_DELETE",
                                      "blockRef": "%s",
                                      "version": 0
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
    @DisplayName("실패_replace_content가 다른 문서 블록을 참조하면 잘못된 요청 응답을 반환한다")
    void applyTransactionsReturnsBadRequestWhenReplaceContentReferencesBlockFromOtherDocument() throws Exception {
        Document targetDocument = document("대상 문서");
        Document otherDocument = document("다른 문서");
        Block otherDocumentBlock = block(otherDocument, null, "다른 문서 블록", "000000000001000000000000");

        mockMvc.perform(post("/documents/{documentId}/transactions", targetDocument.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
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
                                            "text": "잘못된 수정",
                                            "marks": []
                                          }
                                        ]
                                      }
                                    }
                                  ]
                                }
                                """.formatted(otherDocumentBlock.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.code").value(9015))
                .andExpect(jsonPath("$.message").value("잘못된 요청입니다."));

        Block persistedBlock = blockRepository.findByIdAndDeletedAtIsNull(otherDocumentBlock.getId()).orElseThrow();
        assertThat(persistedBlock.getContent()).isEqualTo(content("다른 문서 블록"));
    }

    @Test
    @DisplayName("실패_block_move가 다른 문서 블록을 참조하면 잘못된 요청 응답을 반환한다")
    void applyTransactionsReturnsBadRequestWhenMoveReferencesBlockFromOtherDocument() throws Exception {
        Document targetDocument = document("대상 문서");
        Document otherDocument = document("다른 문서");
        Block otherDocumentBlock = block(otherDocument, null, "다른 문서 블록", "000000000001000000000000");

        mockMvc.perform(post("/documents/{documentId}/transactions", targetDocument.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-1",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_MOVE",
                                      "blockRef": "%s",
                                      "version": 0
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
    @DisplayName("실패_create 뒤 block_move가 충돌하면 앞선 생성까지 전체 transaction을 rollback한다")
    void applyTransactionsRollsBackCreatedBlockWhenLaterMoveConflicts() throws Exception {
        Document document = document("문서");
        Block existingBlock = block(document, null, "기존 블록", "000000000001000000000000");

        existingBlock.setContent(content("다른 사용자 수정"));
        blockRepository.save(existingBlock);

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-move-conflict",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_CREATE",
                                      "blockRef": "tmp:block:1"
                                    },
                                    {
                                      "opId": "op-2",
                                      "type": "BLOCK_MOVE",
                                      "blockRef": "%s",
                                      "version": 0,
                                      "parentRef": "tmp:block:1"
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
    @DisplayName("실패_block_move의 afterRef가 존재하지 않는 temp를 가리키면 잘못된 요청 응답을 반환한다")
    void applyTransactionsReturnsBadRequestWhenMoveAfterRefUsesUnknownTemp() throws Exception {
        Document document = document("문서");
        Block movingBlock = block(document, null, "이동 대상", "000000000001000000000000");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-1",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_MOVE",
                                      "blockRef": "%s",
                                      "version": 0,
                                      "afterRef": "tmp:missing-after"
                                    }
                                  ]
                                }
                                """.formatted(movingBlock.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.code").value(9015))
                .andExpect(jsonPath("$.message").value("잘못된 요청입니다."));
    }

    @Test
    @DisplayName("실패_block_move가 아직 생성되지 않은 temp anchor를 먼저 참조하면 잘못된 요청 응답을 반환한다")
    void applyTransactionsReturnsBadRequestWhenMoveUsesFutureTempAnchor() throws Exception {
        Document document = document("문서");
        Block movingBlock = block(document, null, "이동 대상", "000000000001000000000000");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-1",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_MOVE",
                                      "blockRef": "%s",
                                      "version": 0,
                                      "afterRef": "tmp:future-after"
                                    },
                                    {
                                      "opId": "op-2",
                                      "type": "BLOCK_CREATE",
                                      "blockRef": "tmp:future-after"
                                    }
                                  ]
                                }
                                """.formatted(movingBlock.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.code").value(9015))
                .andExpect(jsonPath("$.message").value("잘못된 요청입니다."));
    }

    @Test
    @DisplayName("실패_block_move가 자기 자신을 afterRef로 참조하면 잘못된 요청 응답을 반환한다")
    void applyTransactionsReturnsBadRequestWhenMoveUsesSelfAsAfterRef() throws Exception {
        Document document = document("문서");
        Block movingBlock = block(document, null, "이동 대상", "000000000001000000000000");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-1",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_MOVE",
                                      "blockRef": "%s",
                                      "version": 0,
                                      "afterRef": "%s"
                                    }
                                  ]
                                }
                                """.formatted(movingBlock.getId(), movingBlock.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.code").value(9015))
                .andExpect(jsonPath("$.message").value("잘못된 요청입니다."));
    }

    @Test
    @DisplayName("실패_block_move의 afterRef와 beforeRef가 역순이면 잘못된 요청 응답을 반환한다")
    void applyTransactionsReturnsBadRequestWhenMoveAnchorsAreReversed() throws Exception {
        Document document = document("문서");
        Block firstBlock = block(document, null, "first", "000000000001000000000000");
        Block middleBlock = block(document, null, "middle", "000000000002000000000000");
        Block lastBlock = block(document, null, "last", "000000000003000000000000");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-1",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_MOVE",
                                      "blockRef": "%s",
                                      "version": 0,
                                      "afterRef": "%s",
                                      "beforeRef": "%s"
                                    }
                                  ]
                                }
                                """.formatted(middleBlock.getId(), lastBlock.getId(), firstBlock.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.code").value(9015))
                .andExpect(jsonPath("$.message").value("잘못된 요청입니다."));
    }

    @Test
    @DisplayName("실패_block_move의 afterRef와 beforeRef가 같은 값을 가리키면 잘못된 요청 응답을 반환한다")
    void applyTransactionsReturnsBadRequestWhenMoveAnchorsPointToSameBlock() throws Exception {
        Document document = document("문서");
        Block movingBlock = block(document, null, "moving", "000000000002000000000000");
        Block anchorBlock = block(document, null, "anchor", "000000000001000000000000");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-1",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_MOVE",
                                      "blockRef": "%s",
                                      "version": 0,
                                      "afterRef": "%s",
                                      "beforeRef": "%s"
                                    }
                                  ]
                                }
                                """.formatted(movingBlock.getId(), anchorBlock.getId(), anchorBlock.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.code").value(9015))
                .andExpect(jsonPath("$.message").value("잘못된 요청입니다."));
    }

    @Test
    @DisplayName("실패_block_move가 대상 parent의 sibling이 아닌 afterRef를 쓰면 잘못된 요청 응답을 반환한다")
    void applyTransactionsReturnsBadRequestWhenMoveUsesAfterRefThatIsNotSiblingOfTargetParent() throws Exception {
        Document document = document("문서");
        Block rootParent = block(document, null, "루트 부모", "000000000001000000000000");
        Block movingBlock = block(document, null, "moving", "000000000002000000000000");
        Block childAnchor = block(document, rootParent, "자식 anchor", "000000000001I00000000000");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-1",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_MOVE",
                                      "blockRef": "%s",
                                      "version": 0,
                                      "afterRef": "%s"
                                    }
                                  ]
                                }
                                """.formatted(movingBlock.getId(), childAnchor.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.code").value(9015))
                .andExpect(jsonPath("$.message").value("잘못된 요청입니다."));
    }

    @Test
    @DisplayName("실패_block_move가 대상 parent의 sibling이 아닌 beforeRef를 쓰면 잘못된 요청 응답을 반환한다")
    void applyTransactionsReturnsBadRequestWhenMoveUsesBeforeRefThatIsNotSiblingOfTargetParent() throws Exception {
        Document document = document("문서");
        Block rootParent = block(document, null, "루트 부모", "000000000001000000000000");
        Block movingBlock = block(document, null, "moving", "000000000002000000000000");
        Block childAnchor = block(document, rootParent, "자식 anchor", "000000000001I00000000000");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-1",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_MOVE",
                                      "blockRef": "%s",
                                      "version": 0,
                                      "beforeRef": "%s"
                                    }
                                  ]
                                }
                                """.formatted(movingBlock.getId(), childAnchor.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.code").value(9015))
                .andExpect(jsonPath("$.message").value("잘못된 요청입니다."));
    }

    @Test
    @DisplayName("성공_block_move가 no-op이면 version을 증가시키지 않는다")
    void applyTransactionsKeepsVersionWhenMoveIsNoOp() throws Exception {
        Document document = document("문서");
        Block movingBlock = block(document, null, "이동 대상", "000000000001000000000000");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-no-op-move",
                                  "operations": [
                                    {
                                      "opId": "op-1",
                                      "type": "BLOCK_MOVE",
                                      "blockRef": "%s",
                                      "version": 0
                                    }
                                  ]
                                }
                                """.formatted(movingBlock.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appliedOperations[0].status").value("NO_OP"))
                .andExpect(jsonPath("$.data.appliedOperations[0].version").value(0));

        Block reloadedMovingBlock = blockRepository.findByIdAndDeletedAtIsNull(movingBlock.getId()).orElseThrow();
        assertThat(reloadedMovingBlock.getVersion()).isEqualTo(0);
        assertThat(reloadedMovingBlock.getSortKey()).isEqualTo("000000000001000000000000");
    }

    @Test
    @DisplayName("성공_existing block replace_content는 본문과 version을 갱신한다")
    void applyTransactionsReplacesExistingBlockContent() throws Exception {
        Document document = document("문서");
        Block existingBlock = block(document, null, "기존 블록", "000000000001000000000000");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
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
    @DisplayName("성공_replace_content가 no-op이면 NO_OP status를 반환하고 version과 updatedBy를 유지한다")
    void applyTransactionsKeepsVersionWhenReplaceContentIsNoOp() throws Exception {
        Document document = document("문서");
        Block existingBlock = block(document, null, "기존 블록", "000000000001000000000000");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "clientId": "web-editor",
                                  "batchId": "batch-no-op-replace",
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
                                            "text": "기존 블록",
                                            "marks": []
                                          }
                                        ]
                                      }
                                    }
                                  ]
                                }
                                """.formatted(existingBlock.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appliedOperations[0].status").value("NO_OP"))
                .andExpect(jsonPath("$.data.appliedOperations[0].version").value(0));

        Block reloadedBlock = blockRepository.findByIdAndDeletedAtIsNull(existingBlock.getId()).orElseThrow();
        assertThat(reloadedBlock.getContent()).isEqualTo(content("기존 블록"));
        assertThat(reloadedBlock.getVersion()).isEqualTo(0);
        assertThat(reloadedBlock.getUpdatedBy()).isEqualTo("user-123");
    }

    @Test
    @DisplayName("실패_존재하지 않는 문서의 transaction 요청은 문서 없음 응답을 반환한다")
    void applyTransactionsReturnsNotFoundWhenDocumentMissing() throws Exception {
        mockMvc.perform(post("/documents/{documentId}/transactions", UUID.randomUUID())
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
    @DisplayName("실패_create의 parentRef가 다른 문서 블록이면 잘못된 요청 응답을 반환한다")
    void applyTransactionsReturnsBadRequestWhenCreateParentBelongsToOtherDocument() throws Exception {
        Document targetDocument = document("대상 문서");
        Document otherDocument = document("다른 문서");
        Block otherDocumentBlock = block(otherDocument, null, "다른 문서 블록", "000000000001000000000000");

        mockMvc.perform(post("/documents/{documentId}/transactions", targetDocument.getId())
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
                                      "parentRef": "%s"
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
    @DisplayName("실패_create의 parentRef가 존재하지 않으면 블록 없음 응답을 반환한다")
    void applyTransactionsReturnsNotFoundWhenCreateParentMissing() throws Exception {
        Document document = document("문서");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
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
                                      "parentRef": "%s"
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
    @DisplayName("실패_create의 afterRef가 활성 sibling이 아니면 잘못된 요청 응답을 반환한다")
    void applyTransactionsReturnsBadRequestWhenCreateAfterBlockIdIsInvalid() throws Exception {
        Document document = document("문서");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
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
                                      "afterRef": "%s"
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
    @DisplayName("실패_create의 afterRef와 beforeRef가 같은 값을 가리키면 잘못된 요청 응답을 반환한다")
    void applyTransactionsReturnsBadRequestWhenCreateAnchorsPointToSameBlock() throws Exception {
        Document document = document("문서");
        Block siblingBlock = block(document, null, "형제 블록", "000000000001000000000000");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
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
                                      "afterRef": "%s",
                                      "beforeRef": "%s"
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

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
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

    @Test
    @DisplayName("성공_create는 temp parentRef를 실제 parentId로 해석해 부모-자식을 함께 저장한다")
    void applyTransactionsCreatesChildUnderTempParent() throws Exception {
        Document document = document("문서");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
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
                                      "blockRef": "tmp:parent"
                                    },
                                    {
                                      "opId": "op-2",
                                      "type": "BLOCK_CREATE",
                                      "blockRef": "tmp:child",
                                      "parentRef": "tmp:parent"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appliedOperations[0].tempId").value("tmp:parent"))
                .andExpect(jsonPath("$.data.appliedOperations[1].tempId").value("tmp:child"));

        assertThat(blockRepository.countActiveByDocumentId(document.getId())).isEqualTo(2);

        Block parentBlock = blockRepository.findActiveByDocumentIdAndParentIdOrderBySortKey(document.getId(), null).get(0);
        Block childBlock = blockRepository.findActiveChildrenByParentIdOrderBySortKey(parentBlock.getId()).get(0);

        assertThat(childBlock.getParentId()).isEqualTo(parentBlock.getId());
    }

    @Test
    @DisplayName("성공_create는 temp afterRef와 beforeRef를 실제 sibling anchor로 해석한다")
    void applyTransactionsCreatesBlockBetweenTempSiblingAnchors() throws Exception {
        Document document = document("문서");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
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
                                      "blockRef": "tmp:first"
                                    },
                                    {
                                      "opId": "op-2",
                                      "type": "BLOCK_REPLACE_CONTENT",
                                      "blockRef": "tmp:first",
                                      "content": {
                                        "format": "rich_text",
                                        "schemaVersion": 1,
                                        "segments": [
                                          {
                                            "text": "first",
                                            "marks": []
                                          }
                                        ]
                                      }
                                    },
                                    {
                                      "opId": "op-3",
                                      "type": "BLOCK_CREATE",
                                      "blockRef": "tmp:last"
                                    },
                                    {
                                      "opId": "op-4",
                                      "type": "BLOCK_REPLACE_CONTENT",
                                      "blockRef": "tmp:last",
                                      "content": {
                                        "format": "rich_text",
                                        "schemaVersion": 1,
                                        "segments": [
                                          {
                                            "text": "last",
                                            "marks": []
                                          }
                                        ]
                                      }
                                    },
                                    {
                                      "opId": "op-5",
                                      "type": "BLOCK_CREATE",
                                      "blockRef": "tmp:middle",
                                      "afterRef": "tmp:first",
                                      "beforeRef": "tmp:last"
                                    },
                                    {
                                      "opId": "op-6",
                                      "type": "BLOCK_REPLACE_CONTENT",
                                      "blockRef": "tmp:middle",
                                      "content": {
                                        "format": "rich_text",
                                        "schemaVersion": 1,
                                        "segments": [
                                          {
                                            "text": "middle",
                                            "marks": []
                                          }
                                        ]
                                      }
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appliedOperations[4].tempId").value("tmp:middle"));

        assertThat(blockRepository.countActiveByDocumentId(document.getId())).isEqualTo(3);

        assertThat(blockRepository.findActiveByDocumentIdOrderBySortKey(document.getId()))
                .extracting(Block::getContent)
                .containsExactly(
                        content("first"),
                        content("middle"),
                        content("last"));
    }

    @Test
    @DisplayName("실패_create의 parentRef가 존재하지 않는 temp를 가리키면 잘못된 요청 응답을 반환한다")
    void applyTransactionsReturnsBadRequestWhenCreateParentRefUsesUnknownTemp() throws Exception {
        Document document = document("문서");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
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
                                      "blockRef": "tmp:child",
                                      "parentRef": "tmp:missing-parent"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.code").value(9015))
                .andExpect(jsonPath("$.message").value("잘못된 요청입니다."));
    }

    @Test
    @DisplayName("실패_create의 afterRef가 존재하지 않는 temp를 가리키면 잘못된 요청 응답을 반환한다")
    void applyTransactionsReturnsBadRequestWhenCreateAfterRefUsesUnknownTemp() throws Exception {
        Document document = document("문서");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
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
                                      "blockRef": "tmp:block",
                                      "afterRef": "tmp:missing-after"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.code").value(9015))
                .andExpect(jsonPath("$.message").value("잘못된 요청입니다."));
    }

    @Test
    @DisplayName("실패_create의 beforeRef가 존재하지 않는 temp를 가리키면 잘못된 요청 응답을 반환한다")
    void applyTransactionsReturnsBadRequestWhenCreateBeforeRefUsesUnknownTemp() throws Exception {
        Document document = document("문서");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
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
                                      "blockRef": "tmp:block",
                                      "beforeRef": "tmp:missing-before"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.code").value(9015))
                .andExpect(jsonPath("$.message").value("잘못된 요청입니다."));
    }

    @Test
    @DisplayName("실패_create가 아직 생성되지 않은 temp anchor를 먼저 참조하면 잘못된 요청 응답을 반환한다")
    void applyTransactionsReturnsBadRequestWhenCreateUsesFutureTempAnchor() throws Exception {
        Document document = document("문서");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
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
                                      "blockRef": "tmp:middle",
                                      "afterRef": "tmp:first"
                                    },
                                    {
                                      "opId": "op-2",
                                      "type": "BLOCK_CREATE",
                                      "blockRef": "tmp:first"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.code").value(9015))
                .andExpect(jsonPath("$.message").value("잘못된 요청입니다."));
    }

    @Test
    @DisplayName("실패_create가 대상 parent의 sibling이 아닌 afterRef를 쓰면 잘못된 요청 응답을 반환한다")
    void applyTransactionsReturnsBadRequestWhenCreateUsesAfterRefThatIsNotSiblingOfTargetParent() throws Exception {
        Document document = document("문서");
        Block rootParent = block(document, null, "루트 부모", "000000000001000000000000");
        Block childAnchor = block(document, rootParent, "자식 anchor", "000000000001I00000000000");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
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
                                      "blockRef": "tmp:block",
                                      "afterRef": "%s"
                                    }
                                  ]
                                }
                                """.formatted(childAnchor.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.code").value(9015))
                .andExpect(jsonPath("$.message").value("잘못된 요청입니다."));
    }

    @Test
    @DisplayName("성공_create는 real afterRef와 temp beforeRef를 함께 해석해 중간 삽입한다")
    void applyTransactionsCreatesBlockBetweenRealAndTempAnchors() throws Exception {
        Document document = document("문서");
        Block realAfterBlock = block(document, null, "real-after", "000000000001000000000000");

        mockMvc.perform(post("/documents/{documentId}/transactions", document.getId())
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
                                      "blockRef": "tmp:before"
                                    },
                                    {
                                      "opId": "op-2",
                                      "type": "BLOCK_REPLACE_CONTENT",
                                      "blockRef": "tmp:before",
                                      "content": {
                                        "format": "rich_text",
                                        "schemaVersion": 1,
                                        "segments": [
                                          {
                                            "text": "before",
                                            "marks": []
                                          }
                                        ]
                                      }
                                    },
                                    {
                                      "opId": "op-3",
                                      "type": "BLOCK_CREATE",
                                      "blockRef": "tmp:middle",
                                      "afterRef": "%s",
                                      "beforeRef": "tmp:before"
                                    },
                                    {
                                      "opId": "op-4",
                                      "type": "BLOCK_REPLACE_CONTENT",
                                      "blockRef": "tmp:middle",
                                      "content": {
                                        "format": "rich_text",
                                        "schemaVersion": 1,
                                        "segments": [
                                          {
                                            "text": "middle",
                                            "marks": []
                                          }
                                        ]
                                      }
                                    }
                                  ]
                                }
                                """.formatted(realAfterBlock.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appliedOperations[2].tempId").value("tmp:middle"));

        assertThat(blockRepository.findActiveByDocumentIdOrderBySortKey(document.getId()))
                .extracting(Block::getContent)
                .containsExactly(
                        content("real-after"),
                        content("middle"),
                        content("before"));
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
