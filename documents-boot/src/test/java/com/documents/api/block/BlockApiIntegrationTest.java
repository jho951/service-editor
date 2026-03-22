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

    private static final String SIMPLE_CONTENT_SERIALIZED = "{\"format\":\"rich_text\",\"schemaVersion\":1,\"segments\":[{\"text\":\"새 블록\",\"marks\":[]}]}";
    private static final String UPDATED_CONTENT_SERIALIZED = "{\"format\":\"rich_text\",\"schemaVersion\":1,\"segments\":[{\"text\":\"수정된 블록\",\"marks\":[]}]}";

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
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .id(UUID.randomUUID())
                .name("Docs Root")
                .build());
        Document document = documentRepository.save(Document.builder()
                .id(UUID.randomUUID())
                .workspace(workspace)
                .title("문서")
                .sortKey("00000000000000000001")
                .build());
        Block rootBlock = blockRepository.save(Block.builder()
                .id(UUID.randomUUID())
                .document(document)
                .type(BlockType.TEXT)
                .content(toContent("루트 블록"))
                .sortKey("000000000001000000000000")
                .createdBy("user-123")
                .updatedBy("user-123")
                .build());
        blockRepository.save(Block.builder()
                .id(UUID.randomUUID())
                .document(document)
                .parent(rootBlock)
                .type(BlockType.TEXT)
                .content(toContent("자식 블록"))
                .sortKey("000000000001I00000000000")
                .createdBy("user-123")
                .updatedBy("user-123")
                .build());
        blockRepository.save(Block.builder()
                .id(UUID.randomUUID())
                .document(document)
                .type(BlockType.TEXT)
                .content(toContent("삭제된 블록"))
                .sortKey("000000000002000000000000")
                .createdBy("user-123")
                .updatedBy("user-123")
                .deletedAt(java.time.LocalDateTime.of(2026, 3, 16, 0, 0))
                .build());

        mockMvc.perform(get("/v1/documents/{documentId}/blocks", document.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.httpStatus").value("OK"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].content.format").value("rich_text"))
                .andExpect(jsonPath("$.data[0].content.segments[0].text").value("루트 블록"))
                .andExpect(jsonPath("$.data[1].content.format").value("rich_text"))
                .andExpect(jsonPath("$.data[1].content.segments[0].text").value("자식 블록"));
    }

    @Test
    @DisplayName("성공_블록 생성 API는 문서 하위에 루트 텍스트 블록을 저장하고 응답한다")
    void createBlockReturnsCreatedEnvelope() throws Exception {
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .id(UUID.randomUUID())
                .name("Docs Root")
                .build());
        Document document = documentRepository.save(Document.builder()
                .id(UUID.randomUUID())
                .workspace(workspace)
                .title("문서")
                .sortKey("00000000000000000001")
                .build());

                mockMvc.perform(post("/v1/documents/{documentId}/blocks", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "type": "TEXT",
                                  "content": {
                                    "format": "rich_text",
                                    "schemaVersion": 1,
                                    "segments": [
                                      {
                                        "text": "새 블록",
                                        "marks": []
                                      }
                                    ]
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.httpStatus").value("CREATED"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("리소스 생성 성공"))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.documentId").value(document.getId().toString()))
                .andExpect(jsonPath("$.data.parentId").doesNotExist())
                .andExpect(jsonPath("$.data.type").value("TEXT"))
                .andExpect(jsonPath("$.data.content.format").value("rich_text"))
                .andExpect(jsonPath("$.data.content.schemaVersion").value(1))
                .andExpect(jsonPath("$.data.content.segments[0].text").value("새 블록"))
                .andExpect(jsonPath("$.data.content.segments[0].marks").isArray())
                .andExpect(jsonPath("$.data.sortKey").value("000000000001000000000000"))
                .andExpect(jsonPath("$.data.createdBy").value("user-123"))
                .andExpect(jsonPath("$.data.version").value(0));

        assertThat(blockRepository.findAll()).hasSize(1);
        Block saved = blockRepository.findAll().get(0);
        assertThat(saved.getDocumentId()).isEqualTo(document.getId());
        assertThat(saved.getParentId()).isNull();
        assertThat(saved.getType()).isEqualTo(BlockType.TEXT);
        assertThat(saved.getContent()).isEqualTo(SIMPLE_CONTENT_SERIALIZED);
        assertThat(saved.getSortKey()).isEqualTo("000000000001000000000000");
        assertThat(saved.getCreatedBy()).isEqualTo("user-123");
    }

    @Test
    @DisplayName("성공_블록 수정 API는 블록 본문과 수정자를 갱신한다")
    void updateBlockUpdatesTextAndActor() throws Exception {
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .id(UUID.randomUUID())
                .name("Docs Root")
                .build());
        Document document = documentRepository.save(Document.builder()
                .id(UUID.randomUUID())
                .workspace(workspace)
                .title("문서")
                .sortKey("00000000000000000001")
                .build());
        Block block = blockRepository.save(Block.builder()
                .id(UUID.randomUUID())
                .document(document)
                .type(BlockType.TEXT)
                .content(toContent("기존 블록"))
                .sortKey("000000000001000000000000")
                .createdBy("user-123")
                .updatedBy("user-123")
                .build());

        mockMvc.perform(patch("/v1/blocks/{blockId}", block.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "content": {
                                    "format": "rich_text",
                                    "schemaVersion": 1,
                                    "segments": [
                                      {
                                        "text": "수정된 블록",
                                        "marks": []
                                      }
                                    ]
                                  },
                                  "version": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.httpStatus").value("OK"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(block.getId().toString()))
                .andExpect(jsonPath("$.data.content.format").value("rich_text"))
                .andExpect(jsonPath("$.data.content.schemaVersion").value(1))
                .andExpect(jsonPath("$.data.content.segments[0].text").value("수정된 블록"))
                .andExpect(jsonPath("$.data.updatedBy").value("user-456"))
                .andExpect(jsonPath("$.data.version").value(1));

        Block updated = blockRepository.findById(block.getId()).orElseThrow();
        assertThat(updated.getContent()).isEqualTo(UPDATED_CONTENT_SERIALIZED);
        assertThat(updated.getUpdatedBy()).isEqualTo("user-456");
        assertThat(updated.getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("실패_블록 수정 API에 낡은 version이 오면 충돌 응답을 반환한다")
    void updateBlockReturnsConflictWhenVersionMismatched() throws Exception {
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .id(UUID.randomUUID())
                .name("Docs Root")
                .build());
        Document document = documentRepository.save(Document.builder()
                .id(UUID.randomUUID())
                .workspace(workspace)
                .title("문서")
                .sortKey("00000000000000000001")
                .build());
        Block block = blockRepository.save(Block.builder()
                .id(UUID.randomUUID())
                .document(document)
                .type(BlockType.TEXT)
                .content(toContent("기존 블록"))
                .sortKey("000000000001000000000000")
                .createdBy("user-123")
                .updatedBy("user-123")
                .build());
        block.setContent(toContent("다른 사용자 수정"));
        blockRepository.save(block);

        mockMvc.perform(patch("/v1/blocks/{blockId}", block.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "content": {
                                    "format": "rich_text",
                                    "schemaVersion": 1,
                                    "segments": [
                                      {
                                        "text": "수정된 블록",
                                        "marks": []
                                      }
                                    ]
                                  },
                                  "version": 0
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.httpStatus").value("CONFLICT"))
                .andExpect(jsonPath("$.code").value(9005))
                .andExpect(jsonPath("$.message").value("요청이 현재 리소스 상태와 충돌합니다."));
    }

    @Test
    @DisplayName("성공_블록 삭제 API는 하위 블록까지 soft delete 처리하고 다른 블록은 보존한다")
    void deleteBlockSoftDeletesDescendantsOnly() throws Exception {
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .id(UUID.randomUUID())
                .name("Docs Root")
                .build());
        Document document = documentRepository.save(Document.builder()
                .id(UUID.randomUUID())
                .workspace(workspace)
                .title("문서")
                .sortKey("00000000000000000001")
                .build());
        Block rootBlock = saveBlock(document, null, "삭제 대상 루트", "000000000001000000000000");
        Block childBlock = saveBlock(document, rootBlock, "삭제 대상 자식", "000000000001I00000000000");
        Block grandChildBlock = saveBlock(document, childBlock, "삭제 대상 손자", "000000000001Q00000000000");
        Block otherRootBlock = saveBlock(document, null, "보존 대상", "000000000002000000000000");

        mockMvc.perform(delete("/v1/blocks/{blockId}", rootBlock.getId())
                        .param("version", "0")
                        .header("X-User-Id", "user-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.httpStatus").value("OK"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200));

        Block deletedRoot = blockRepository.findById(rootBlock.getId()).orElseThrow();
        Block deletedChild = blockRepository.findById(childBlock.getId()).orElseThrow();
        Block deletedGrandChild = blockRepository.findById(grandChildBlock.getId()).orElseThrow();
        Block survivedBlock = blockRepository.findById(otherRootBlock.getId()).orElseThrow();

        assertThat(deletedRoot.getDeletedAt()).isNotNull();
        assertThat(deletedChild.getDeletedAt()).isNotNull();
        assertThat(deletedGrandChild.getDeletedAt()).isNotNull();
        assertThat(survivedBlock.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("실패_존재하지 않는 블록을 삭제하면 블록 없음 응답을 반환한다")
    void deleteBlockReturnsNotFoundWhenBlockMissing() throws Exception {
        var result = mockMvc.perform(delete("/v1/blocks/{blockId}", UUID.randomUUID())
                        .param("version", "0")
                        .header("X-User-Id", "user-123"));

        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.httpStatus").value("NOT_FOUND"))
                .andExpect(jsonPath("$.code").value(9006))
                .andExpect(jsonPath("$.message").value("요청한 블록을 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("실패_삭제 version이 낡으면 충돌 응답을 반환한다")
    void deleteBlockReturnsConflictWhenVersionIsStale() throws Exception {
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .id(UUID.randomUUID())
                .name("Docs Root")
                .build());
        Document document = documentRepository.save(Document.builder()
                .id(UUID.randomUUID())
                .workspace(workspace)
                .title("문서")
                .sortKey("00000000000000000001")
                .build());
        Block rootBlock = saveBlock(document, null, "삭제 대상 루트", "000000000001000000000000");

        mockMvc.perform(delete("/v1/blocks/{blockId}", rootBlock.getId())
                        .param("version", "-1")
                        .header("X-User-Id", "user-123"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.httpStatus").value("CONFLICT"))
                .andExpect(jsonPath("$.code").value(9005))
                .andExpect(jsonPath("$.message").value("요청이 현재 리소스 상태와 충돌합니다."));
    }

    @Test
    @DisplayName("성공_afterBlockId를 사용하면 기존 형제 사이 위치용 gap sortKey로 블록을 생성한다")
    void createBlockInsertsBetweenSiblings() throws Exception {
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .id(UUID.randomUUID())
                .name("Docs Root")
                .build());
        Document document = documentRepository.save(Document.builder()
                .id(UUID.randomUUID())
                .workspace(workspace)
                .title("문서")
                .sortKey("00000000000000000001")
                .build());
        Block first = blockRepository.save(Block.builder()
                .id(UUID.randomUUID())
                .document(document)
                .type(BlockType.TEXT)
                .content(toContent("첫 블록"))
                .sortKey("000000000001000000000000")
                .createdBy("user-123")
                .updatedBy("user-123")
                .build());
        Block second = blockRepository.save(Block.builder()
                .id(UUID.randomUUID())
                .document(document)
                .type(BlockType.TEXT)
                .content(toContent("둘째 블록"))
                .sortKey("000000000002000000000000")
                .createdBy("user-123")
                .updatedBy("user-123")
                .build());

        mockMvc.perform(post("/v1/documents/{documentId}/blocks", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "type": "TEXT",
                                  "content": {
                                    "format": "rich_text",
                                    "schemaVersion": 1,
                                    "segments": [
                                      {
                                        "text": "중간 블록",
                                        "marks": []
                                      }
                                    ]
                                  },
                                  "afterBlockId": "%s"
                                }
                                """.formatted(first.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sortKey").value("000000000001I00000000000"));

        Block reloadedSecond = blockRepository.findById(second.getId()).orElseThrow();
        assertThat(reloadedSecond.getSortKey()).isEqualTo("000000000002000000000000");
    }

    @Test
    @DisplayName("실패_존재하지 않는 문서로 블록을 생성하면 문서 없음 응답을 반환한다")
    void createBlockReturnsNotFoundWhenDocumentMissing() throws Exception {
        var result = mockMvc.perform(post("/v1/documents/{documentId}/blocks", UUID.randomUUID())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "type": "TEXT",
                                  "content": {
                                    "format": "rich_text",
                                    "schemaVersion": 1,
                                    "segments": [
                                      {
                                        "text": "새 블록",
                                        "marks": []
                                      }
                                    ]
                                  }
                                }
                                """));

        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.httpStatus").value("NOT_FOUND"))
                .andExpect(jsonPath("$.code").value(9004))
                .andExpect(jsonPath("$.message").value("요청한 문서를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("성공_루트 블록을 다른 루트 위치로 재정렬하면 sortKey와 updatedBy와 version이 변경된다")
    void moveRootBlockReordersAmongRootBlocks() throws Exception {
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .id(UUID.randomUUID())
                .name("Docs Root")
                .build());
        Document document = documentRepository.save(Document.builder()
                .id(UUID.randomUUID())
                .workspace(workspace)
                .title("문서")
                .sortKey("00000000000000000001")
                .build());
        Block first = saveBlock(document, null, "첫 블록", "000000000001000000000000");
        Block moved = saveBlock(document, null, "이동 대상", "000000000002000000000000");
        Block third = saveBlock(document, null, "셋째 블록", "000000000003000000000000");

        mockMvc.perform(post("/v1/blocks/{blockId}/move", moved.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "parentId": null,
                                  "afterBlockId": "%s",
                                  "beforeBlockId": null,
                                  "version": 0
                                }
                                """.formatted(third.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.httpStatus").value("OK"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200));

        Block reloaded = blockRepository.findById(moved.getId()).orElseThrow();
        assertThat(reloaded.getParentId()).isNull();
        assertThat(reloaded.getSortKey()).isEqualTo("000000000004000000000000");
        assertThat(reloaded.getUpdatedBy()).isEqualTo("user-456");
        assertThat(reloaded.getVersion()).isEqualTo(1);
        assertThat(blockRepository.findById(first.getId()).orElseThrow().getSortKey())
                .isEqualTo("000000000001000000000000");
    }

    @Test
    @DisplayName("성공_블록을 다른 부모 블록 아래로 이동하면 parentId와 sortKey가 함께 변경된다")
    void moveBlockToAnotherParent() throws Exception {
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .id(UUID.randomUUID())
                .name("Docs Root")
                .build());
        Document document = documentRepository.save(Document.builder()
                .id(UUID.randomUUID())
                .workspace(workspace)
                .title("문서")
                .sortKey("00000000000000000001")
                .build());
        Block parent = saveBlock(document, null, "대상 부모", "000000000001000000000000");
        saveBlock(document, parent, "기존 자식", "000000000001000000000000");
        Block moved = saveBlock(document, null, "이동 대상", "000000000002000000000000");

        mockMvc.perform(post("/v1/blocks/{blockId}/move", moved.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "parentId": "%s",
                                  "afterBlockId": null,
                                  "beforeBlockId": null,
                                  "version": 0
                                }
                                """.formatted(parent.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.httpStatus").value("OK"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200));

        Block reloaded = blockRepository.findById(moved.getId()).orElseThrow();
        assertThat(reloaded.getParentId()).isEqualTo(parent.getId());
        assertThat(reloaded.getSortKey()).isEqualTo("000000000002000000000000");
        assertThat(reloaded.getUpdatedBy()).isEqualTo("user-456");
        assertThat(reloaded.getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("성공_same parent 내 afterBlockId 기준 이동이 반영된다")
    void moveBlockWithinSameParentUsingAfterAnchor() throws Exception {
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .id(UUID.randomUUID())
                .name("Docs Root")
                .build());
        Document document = documentRepository.save(Document.builder()
                .id(UUID.randomUUID())
                .workspace(workspace)
                .title("문서")
                .sortKey("00000000000000000001")
                .build());
        Block parent = saveBlock(document, null, "부모", "000000000001000000000000");
        Block first = saveBlock(document, parent, "첫 자식", "000000000001000000000000");
        Block second = saveBlock(document, parent, "둘째 자식", "000000000002000000000000");
        Block moved = saveBlock(document, parent, "이동 대상", "000000000003000000000000");

        mockMvc.perform(post("/v1/blocks/{blockId}/move", moved.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "parentId": "%s",
                                  "afterBlockId": "%s",
                                  "beforeBlockId": null,
                                  "version": 0
                                }
                                """.formatted(parent.getId(), first.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.httpStatus").value("OK"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200));

        Block reloaded = blockRepository.findById(moved.getId()).orElseThrow();
        assertThat(reloaded.getParentId()).isEqualTo(parent.getId());
        assertThat(reloaded.getSortKey()).isEqualTo("000000000001I00000000000");
        assertThat(reloaded.getUpdatedBy()).isEqualTo("user-456");
        assertThat(blockRepository.findById(second.getId()).orElseThrow().getSortKey())
                .isEqualTo("000000000002000000000000");
    }

    @Test
    @DisplayName("실패_존재하지 않는 블록 이동은 블록 없음 응답을 반환한다")
    void moveBlockReturnsNotFoundWhenBlockMissing() throws Exception {
        var result = mockMvc.perform(post("/v1/blocks/{blockId}/move", UUID.randomUUID())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "parentId": null,
                                  "afterBlockId": null,
                                  "beforeBlockId": null,
                                  "version": 0
                                }
                                """));

        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.httpStatus").value("NOT_FOUND"))
                .andExpect(jsonPath("$.code").value(9006))
                .andExpect(jsonPath("$.message").value("요청한 블록을 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("실패_삭제된 블록 이동은 블록 없음 응답을 반환한다")
    void moveBlockReturnsNotFoundWhenBlockDeleted() throws Exception {
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .id(UUID.randomUUID())
                .name("Docs Root")
                .build());
        Document document = documentRepository.save(Document.builder()
                .id(UUID.randomUUID())
                .workspace(workspace)
                .title("문서")
                .sortKey("00000000000000000001")
                .build());
        Block deleted = blockRepository.save(Block.builder()
                .id(UUID.randomUUID())
                .document(document)
                .type(BlockType.TEXT)
                .content(toContent("삭제 블록"))
                .sortKey("000000000001000000000000")
                .createdBy("user-123")
                .updatedBy("user-123")
                .deletedAt(java.time.LocalDateTime.of(2026, 3, 20, 0, 0))
                .build());

        var result = mockMvc.perform(post("/v1/blocks/{blockId}/move", deleted.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "parentId": null,
                                  "afterBlockId": null,
                                  "beforeBlockId": null,
                                  "version": 0
                                }
                                """));

        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.httpStatus").value("NOT_FOUND"))
                .andExpect(jsonPath("$.code").value(9006))
                .andExpect(jsonPath("$.message").value("요청한 블록을 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("실패_블록 이동 API에 낡은 version이 오면 충돌 응답을 반환한다")
    void moveBlockReturnsConflictWhenVersionMismatched() throws Exception {
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .id(UUID.randomUUID())
                .name("Docs Root")
                .build());
        Document document = documentRepository.save(Document.builder()
                .id(UUID.randomUUID())
                .workspace(workspace)
                .title("문서")
                .sortKey("00000000000000000001")
                .build());
        Block block = saveBlock(document, null, "이동 대상", "000000000001000000000000");
        block.setContent(toContent("다른 사용자 수정"));
        blockRepository.save(block);

        mockMvc.perform(post("/v1/blocks/{blockId}/move", block.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content("""
                                {
                                  "parentId": null,
                                  "afterBlockId": null,
                                  "beforeBlockId": null,
                                  "version": 0
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.httpStatus").value("CONFLICT"))
                .andExpect(jsonPath("$.code").value(9005))
                .andExpect(jsonPath("$.message").value("요청이 현재 리소스 상태와 충돌합니다."));
    }

    @Test
    @DisplayName("실패_자기 자신을 부모로 지정하면 잘못된 요청 응답을 반환한다")
    void moveBlockReturnsBadRequestWhenParentIsSelf() throws Exception {
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .id(UUID.randomUUID())
                .name("Docs Root")
                .build());
        Document document = documentRepository.save(Document.builder()
                .id(UUID.randomUUID())
                .workspace(workspace)
                .title("문서")
                .sortKey("00000000000000000001")
                .build());
        Block block = saveBlock(document, null, "이동 대상", "000000000001000000000000");

        mockMvc.perform(post("/v1/blocks/{blockId}/move", block.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "parentId": "%s",
                                  "afterBlockId": null,
                                  "beforeBlockId": null,
                                  "version": 0
                                }
                                """.formatted(block.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.code").value(9015))
                .andExpect(jsonPath("$.message").value("잘못된 요청입니다."));
    }

    @Test
    @DisplayName("실패_하위 블록을 부모로 지정하면 잘못된 요청 응답을 반환한다")
    void moveBlockReturnsBadRequestWhenParentIsDescendant() throws Exception {
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .id(UUID.randomUUID())
                .name("Docs Root")
                .build());
        Document document = documentRepository.save(Document.builder()
                .id(UUID.randomUUID())
                .workspace(workspace)
                .title("문서")
                .sortKey("00000000000000000001")
                .build());
        Block root = saveBlock(document, null, "루트", "000000000001000000000000");
        Block child = saveBlock(document, root, "자식", "000000000001000000000000");

        mockMvc.perform(post("/v1/blocks/{blockId}/move", root.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "parentId": "%s",
                                  "afterBlockId": null,
                                  "beforeBlockId": null,
                                  "version": 0
                                }
                                """.formatted(child.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.code").value(9015))
                .andExpect(jsonPath("$.message").value("잘못된 요청입니다."));
    }

    @Test
    @DisplayName("실패_최대 깊이를 넘기는 부모로 블록 이동 요청 시 잘못된 요청 응답을 반환한다")
    void moveBlockReturnsBadRequestWhenTargetParentDepthExceedsLimit() throws Exception {
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .id(UUID.randomUUID())
                .name("Docs Root")
                .build());
        Document document = documentRepository.save(Document.builder()
                .id(UUID.randomUUID())
                .workspace(workspace)
                .title("문서")
                .sortKey("00000000000000000001")
                .build());
        Block depth1 = saveBlock(document, null, "1", "000000000001000000000000");
        Block depth2 = saveBlock(document, depth1, "2", "000000000001000000000000");
        Block depth3 = saveBlock(document, depth2, "3", "000000000001000000000000");
        Block depth4 = saveBlock(document, depth3, "4", "000000000001000000000000");
        Block depth5 = saveBlock(document, depth4, "5", "000000000001000000000000");
        Block depth6 = saveBlock(document, depth5, "6", "000000000001000000000000");
        Block depth7 = saveBlock(document, depth6, "7", "000000000001000000000000");
        Block depth8 = saveBlock(document, depth7, "8", "000000000001000000000000");
        Block depth9 = saveBlock(document, depth8, "9", "000000000001000000000000");
        Block depth10 = saveBlock(document, depth9, "10", "000000000001000000000000");
        Block moved = saveBlock(document, null, "이동 대상", "000000000002000000000000");

        mockMvc.perform(post("/v1/blocks/{blockId}/move", moved.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "parentId": "%s",
                                  "afterBlockId": null,
                                  "beforeBlockId": null,
                                  "version": 0
                                }
                                """.formatted(depth10.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.code").value(9015))
                .andExpect(jsonPath("$.message").value("잘못된 요청입니다."));
    }

    @Test
    @DisplayName("실패_다른 문서의 블록을 부모나 anchor로 사용하면 잘못된 요청 응답을 반환한다")
    void moveBlockReturnsBadRequestWhenParentOrAnchorBelongsToOtherDocument() throws Exception {
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .id(UUID.randomUUID())
                .name("Docs Root")
                .build());
        Document sourceDocument = documentRepository.save(Document.builder()
                .id(UUID.randomUUID())
                .workspace(workspace)
                .title("원본 문서")
                .sortKey("00000000000000000001")
                .build());
        Document otherDocument = documentRepository.save(Document.builder()
                .id(UUID.randomUUID())
                .workspace(workspace)
                .title("다른 문서")
                .sortKey("00000000000000000002")
                .build());
        Block moved = saveBlock(sourceDocument, null, "이동 대상", "000000000001000000000000");
        Block otherParent = saveBlock(otherDocument, null, "다른 부모", "000000000001000000000000");
        Block otherAnchor = saveBlock(otherDocument, null, "다른 anchor", "000000000002000000000000");

        mockMvc.perform(post("/v1/blocks/{blockId}/move", moved.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "parentId": "%s",
                                  "afterBlockId": "%s",
                                  "beforeBlockId": null,
                                  "version": 0
                                }
                                """.formatted(otherParent.getId(), otherAnchor.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.code").value(9015))
                .andExpect(jsonPath("$.message").value("잘못된 요청입니다."));
    }

    @Test
    @DisplayName("실패_블록 생성 API에 허용되지 않은 mark가 오면 유효성 검사 오류를 반환한다")
    void createBlockRejectsUnsupportedMarkType() throws Exception {
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .id(UUID.randomUUID())
                .name("Docs Root")
                .build());
        Document document = documentRepository.save(Document.builder()
                .id(UUID.randomUUID())
                .workspace(workspace)
                .title("문서")
                .sortKey("00000000000000000001")
                .build());

        mockMvc.perform(post("/v1/documents/{documentId}/blocks", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "type": "TEXT",
                                  "content": {
                                    "format": "rich_text",
                                    "schemaVersion": 1,
                                    "segments": [
                                      {
                                        "text": "새 블록",
                                        "marks": [
                                          {
                                            "type": "link",
                                            "value": "https://example.com"
                                          }
                                        ]
                                      }
                                    ]
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.code").value(9016))
                .andExpect(jsonPath("$.message").value("요청 필드 유효성 검사에 실패했습니다."));
    }

    @Test
    @DisplayName("실패_블록 생성 API에 중복 mark 타입이 오면 유효성 검사 오류를 반환한다")
    void createBlockRejectsDuplicateMarkType() throws Exception {
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .id(UUID.randomUUID())
                .name("Docs Root")
                .build());
        Document document = documentRepository.save(Document.builder()
                .id(UUID.randomUUID())
                .workspace(workspace)
                .title("문서")
                .sortKey("00000000000000000001")
                .build());

        mockMvc.perform(post("/v1/documents/{documentId}/blocks", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "type": "TEXT",
                                  "content": {
                                    "format": "rich_text",
                                    "schemaVersion": 1,
                                    "segments": [
                                      {
                                        "text": "새 블록",
                                        "marks": [
                                          { "type": "bold" },
                                          { "type": "bold" }
                                        ]
                                      }
                                    ]
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.code").value(9016))
                .andExpect(jsonPath("$.message").value("요청 필드 유효성 검사에 실패했습니다."));
    }

    private String toContent(String text) {
        return "{\"format\":\"rich_text\",\"schemaVersion\":1,\"segments\":[{\"text\":\"%s\",\"marks\":[]}]}".formatted(text);
    }

    private Block saveBlock(Document document, Block parent, String text, String sortKey) {
        return blockRepository.save(Block.builder()
                .id(UUID.randomUUID())
                .document(document)
                .parent(parent)
                .type(BlockType.TEXT)
                .content(toContent(text))
                .sortKey(sortKey)
                .createdBy("user-123")
                .updatedBy("user-123")
                .build());
    }
}
