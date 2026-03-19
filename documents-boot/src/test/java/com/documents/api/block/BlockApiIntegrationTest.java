package com.documents.api.block;

import static org.assertj.core.api.Assertions.assertThat;
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
                .text("루트 블록")
                .sortKey("000000000001000000000000")
                .createdBy("user-123")
                .updatedBy("user-123")
                .build());
        blockRepository.save(Block.builder()
                .id(UUID.randomUUID())
                .document(document)
                .parent(rootBlock)
                .type(BlockType.TEXT)
                .text("자식 블록")
                .sortKey("000000000001I00000000000")
                .createdBy("user-123")
                .updatedBy("user-123")
                .build());
        blockRepository.save(Block.builder()
                .id(UUID.randomUUID())
                .document(document)
                .type(BlockType.TEXT)
                .text("삭제된 블록")
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
                .andExpect(jsonPath("$.data[0].text").value("루트 블록"))
                .andExpect(jsonPath("$.data[1].text").value("자식 블록"));
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
                .text("기존 블록")
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
                .text("기존 블록")
                .sortKey("000000000001000000000000")
                .createdBy("user-123")
                .updatedBy("user-123")
                .build());
        block.setText("다른 사용자 수정");
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
                .text("첫 블록")
                .sortKey("000000000001000000000000")
                .createdBy("user-123")
                .updatedBy("user-123")
                .build());
        Block second = blockRepository.save(Block.builder()
                .id(UUID.randomUUID())
                .document(document)
                .type(BlockType.TEXT)
                .text("둘째 블록")
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

}
