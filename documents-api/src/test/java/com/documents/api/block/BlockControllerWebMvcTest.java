package com.documents.api.block;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.documents.api.block.support.BlockJsonCodec;
import com.documents.api.exception.GlobalExceptionHandler;
import com.documents.api.support.ApiResponseAssertions;
import com.documents.domain.Block;
import com.documents.domain.BlockType;
import com.documents.domain.Document;
import com.documents.domain.Workspace;
import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.service.BlockService;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("Block 컨트롤러 빠른 검증")
class BlockControllerWebMvcTest {

    private static final String SIMPLE_CONTENT_SERIALIZED = "{\"format\":\"rich_text\",\"schemaVersion\":1,\"segments\":[{\"text\":\"새 블록\",\"marks\":[]}]}";
    private static final String UPDATED_CONTENT_SERIALIZED = "{\"format\":\"rich_text\",\"schemaVersion\":1,\"segments\":[{\"text\":\"수정된 블록\",\"marks\":[]}]}";
    private static final String ROOT_CONTENT_SERIALIZED = "{\"format\":\"rich_text\",\"schemaVersion\":1,\"segments\":[{\"text\":\"루트 블록\",\"marks\":[]}]}";
    private static final String CHILD_CONTENT_SERIALIZED = "{\"format\":\"rich_text\",\"schemaVersion\":1,\"segments\":[{\"text\":\"자식 블록\",\"marks\":[]}]}";

    @Mock
    private BlockService blockService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        BlockJsonCodec blockJsonCodec = new BlockJsonCodec(new ObjectMapper());

        mockMvc = MockMvcBuilders.standaloneSetup(new BlockController(
                        blockService,
                        new BlockApiMapper(blockJsonCodec),
                        blockJsonCodec
                ))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("성공_문서 블록 목록 조회 요청에 대해 활성 블록 전체를 반환한다")
    void getBlocksReturnsAllActiveBlocks() throws Exception {
        UUID documentId = UUID.randomUUID();
        UUID rootBlockId = UUID.randomUUID();
        UUID childBlockId = UUID.randomUUID();

        when(blockService.getAllByDocumentId(documentId)).thenReturn(List.of(
                block(rootBlockId, documentId, null, "000000000001000000000000", 0, "루트 블록"),
                block(childBlockId, documentId, rootBlockId, "000000000001I00000000000", 1, "자식 블록")
        ));

        mockMvc.perform(get("/v1/documents/{documentId}/blocks", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.httpStatus").value("OK"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(rootBlockId.toString()))
                .andExpect(jsonPath("$.data[0].content.format").value("rich_text"))
                .andExpect(jsonPath("$.data[0].content.segments[0].text").value("루트 블록"))
                .andExpect(jsonPath("$.data[1].parentId").value(rootBlockId.toString()))
                .andExpect(jsonPath("$.data[1].content.format").value("rich_text"))
                .andExpect(jsonPath("$.data[1].content.segments[0].text").value("자식 블록"));
    }

    @Test
    @DisplayName("성공_블록 생성 요청에 대해 gap 기반 sortKey 전략의 생성 응답을 반환한다")
    void createBlockReturnsCreatedEnvelope() throws Exception {
        UUID documentId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();

        Block createdBlock = block(
                blockId,
                documentId,
                parentId,
                "000000000001000000000000",
                0,
                "새 블록"
        );
        createdBlock.setContent("{\"format\":\"rich_text\",\"schemaVersion\":1,\"segments\":[{\"text\":\"새 블록\",\"marks\":[]}]}");

        when(blockService.create(
                eq(documentId),
                eq(parentId),
                eq(BlockType.TEXT),
                eq(SIMPLE_CONTENT_SERIALIZED),
                eq(null),
                eq(null),
                eq("user-123")
        )).thenReturn(createdBlock);

        mockMvc.perform(post("/v1/documents/{documentId}/blocks", documentId)
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "parentId": "%s",
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
                                """.formatted(parentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.httpStatus").value("CREATED"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.id").value(blockId.toString()))
                .andExpect(jsonPath("$.data.documentId").value(documentId.toString()))
                .andExpect(jsonPath("$.data.parentId").value(parentId.toString()))
                .andExpect(jsonPath("$.data.type").value("TEXT"))
                .andExpect(jsonPath("$.data.content.format").value("rich_text"))
                .andExpect(jsonPath("$.data.content.schemaVersion").value(1))
                .andExpect(jsonPath("$.data.content.segments[0].text").value("새 블록"))
                .andExpect(jsonPath("$.data.content.segments[0].marks").isArray())
                .andExpect(jsonPath("$.data.sortKey").value("000000000001000000000000"))
                .andExpect(jsonPath("$.data.createdBy").value("user-123"))
                .andExpect(jsonPath("$.data.version").value(0));
    }

    @Test
    @DisplayName("성공_블록 수정 요청에 대해 수정 응답을 반환한다")
    void updateBlockReturnsUpdatedEnvelope() throws Exception {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();

        Block updatedBlock = block(
                        blockId,
                        documentId,
                        null,
                        "000000000001000000000000",
                        1,
                        "수정된 블록"
                );
        updatedBlock.setContent(UPDATED_CONTENT_SERIALIZED);

        when(blockService.update(eq(blockId), eq(UPDATED_CONTENT_SERIALIZED), eq(0), eq("user-123")))
                .thenReturn(updatedBlock);

        mockMvc.perform(patch("/v1/blocks/{blockId}", blockId)
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
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
                .andExpect(jsonPath("$.data.id").value(blockId.toString()))
                .andExpect(jsonPath("$.data.content.format").value("rich_text"))
                .andExpect(jsonPath("$.data.content.schemaVersion").value(1))
                .andExpect(jsonPath("$.data.content.segments[0].text").value("수정된 블록"))
                .andExpect(jsonPath("$.data.version").value(1));
    }

    @Test
    @DisplayName("실패_type이 없으면 유효성 검사 오류를 반환한다")
    void createBlockRejectsMissingType() throws Exception {
        var result = mockMvc.perform(post("/v1/documents/{documentId}/blocks", UUID.randomUUID())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
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

        ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9016, "요청 필드 유효성 검사에 실패했습니다.");
    }

    @Test
    @DisplayName("실패_부모 블록이 없으면 블록 없음 응답을 반환한다")
    void createBlockReturnsNotFoundWhenParentMissing() throws Exception {
        UUID documentId = UUID.randomUUID();
        UUID parentId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        when(blockService.create(
                eq(documentId),
                eq(parentId),
                eq(BlockType.TEXT),
                eq(SIMPLE_CONTENT_SERIALIZED),
                eq(null),
                eq(null),
                eq("user-123")
        )).thenThrow(new BusinessException(BusinessErrorCode.BLOCK_NOT_FOUND));

        var result = mockMvc.perform(post("/v1/documents/{documentId}/blocks", documentId)
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "parentId": "11111111-1111-1111-1111-111111111111",
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

        ApiResponseAssertions.assertErrorEnvelope(result, "NOT_FOUND", 9006, "요청한 블록을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("실패_인증 헤더가 없으면 블록 생성 API는 인증 오류를 반환한다")
    void createBlockReturnsUnauthorizedWhenHeaderMissing() throws Exception {
        var result = mockMvc.perform(post("/v1/documents/{documentId}/blocks", UUID.randomUUID())
                        .contentType("application/json")
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

        ApiResponseAssertions.assertErrorEnvelope(result, "UNAUTHORIZED", 9001, "인증 정보가 없습니다.");
    }

    @Test
    @DisplayName("실패_content가 없으면 유효성 검사 오류를 반환한다")
    void createBlockRejectsMissingContent() throws Exception {
        var result = mockMvc.perform(post("/v1/documents/{documentId}/blocks", UUID.randomUUID())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "type": "TEXT"
                                }
                                """));

        ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9016, "요청 필드 유효성 검사에 실패했습니다.");
    }

    @Test
    @DisplayName("실패_content format이 다르면 유효성 검사 오류를 반환한다")
    void createBlockRejectsUnsupportedContentFormat() throws Exception {
        var result = mockMvc.perform(post("/v1/documents/{documentId}/blocks", UUID.randomUUID())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "type": "TEXT",
                                  "content": {
                                    "format": "plain_text",
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

        ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9016, "요청 필드 유효성 검사에 실패했습니다.");
    }

    @Test
    @DisplayName("실패_mark 타입이 허용 목록이 아니면 유효성 검사 오류를 반환한다")
    void createBlockRejectsUnsupportedMarkType() throws Exception {
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
                                """));

        ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9016, "요청 필드 유효성 검사에 실패했습니다.");
    }

    @Test
    @DisplayName("실패_textColor 값이 hex 형식이 아니면 유효성 검사 오류를 반환한다")
    void createBlockRejectsInvalidTextColor() throws Exception {
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
                                        "marks": [
                                          {
                                            "type": "textColor",
                                            "value": "black"
                                          }
                                        ]
                                      }
                                    ]
                                  }
                                }
                                """));

        ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9016, "요청 필드 유효성 검사에 실패했습니다.");
    }

    @Test
    @DisplayName("실패_같은 segment에 중복 mark 타입이 있으면 유효성 검사 오류를 반환한다")
    void createBlockRejectsDuplicateMarkType() throws Exception {
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
                                        "marks": [
                                          { "type": "bold" },
                                          { "type": "bold" }
                                        ]
                                      }
                                    ]
                                  }
                                }
                                """));

        ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9016, "요청 필드 유효성 검사에 실패했습니다.");
    }

    @Test
    @DisplayName("실패_textColor 외 mark에 value가 있으면 유효성 검사 오류를 반환한다")
    void createBlockRejectsUnexpectedMarkValue() throws Exception {
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
                                        "marks": [
                                          {
                                            "type": "bold",
                                            "value": true
                                          }
                                        ]
                                      }
                                    ]
                                  }
                                }
                                """));

        ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9016, "요청 필드 유효성 검사에 실패했습니다.");
    }

    @Test
    @DisplayName("실패_segment에 허용되지 않은 필드가 있으면 유효성 검사 오류를 반환한다")
    void createBlockRejectsUnexpectedSegmentField() throws Exception {
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
                                        "marks": [],
                                        "extra": "field"
                                      }
                                    ]
                                  }
                                }
                                """));

        ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9016, "요청 필드 유효성 검사에 실패했습니다.");
    }

    @Test
    @DisplayName("실패_빈 segment가 중간에 섞여 있으면 유효성 검사 오류를 반환한다")
    void createBlockRejectsEmptySegmentInMultiSegmentContent() throws Exception {
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
                                        "text": "",
                                        "marks": []
                                      },
                                      {
                                        "text": "새 블록",
                                        "marks": []
                                      }
                                    ]
                                  }
                                }
                                """));

        ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9016, "요청 필드 유효성 검사에 실패했습니다.");
    }

    @Test
    @DisplayName("성공_segment가 1개뿐인 빈 블록은 유효성 검사에 통과한다")
    void createBlockAllowsSingleEmptySegment() throws Exception {
        UUID documentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        Block createdBlock = block(
                blockId,
                documentId,
                null,
                "000000000001000000000000",
                0,
                ""
        );
        createdBlock.setContent("{\"format\":\"rich_text\",\"schemaVersion\":1,\"segments\":[{\"text\":\"\",\"marks\":[]}]}");

        when(blockService.create(
                eq(documentId),
                eq(null),
                eq(BlockType.TEXT),
                eq("{\"format\":\"rich_text\",\"schemaVersion\":1,\"segments\":[{\"text\":\"\",\"marks\":[]}]}"),
                eq(null),
                eq(null),
                eq("user-123")
        )).thenReturn(createdBlock);

        mockMvc.perform(post("/v1/documents/{documentId}/blocks", documentId)
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
                                        "text": "",
                                        "marks": []
                                      }
                                    ]
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.content.segments[0].text").value(""));
    }

    @Test
    @DisplayName("실패_content가 없으면 블록 수정 요청은 유효성 검사 오류를 반환한다")
    void updateBlockRejectsMissingContent() throws Exception {
        var result = mockMvc.perform(patch("/v1/blocks/{blockId}", UUID.randomUUID())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "version": 0
                                }
                                """));

        ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9016, "요청 필드 유효성 검사에 실패했습니다.");
    }

    @Test
    @DisplayName("실패_존재하지 않는 블록이면 블록 없음 응답을 반환한다")
    void updateBlockReturnsNotFoundWhenBlockMissing() throws Exception {
        UUID blockId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        when(blockService.update(eq(blockId), eq(UPDATED_CONTENT_SERIALIZED), eq(0), eq("user-123")))
                .thenThrow(new BusinessException(BusinessErrorCode.BLOCK_NOT_FOUND));

        var result = mockMvc.perform(patch("/v1/blocks/{blockId}", blockId)
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
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
                                """));

        ApiResponseAssertions.assertErrorEnvelope(result, "NOT_FOUND", 9006, "요청한 블록을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("실패_인증 헤더가 없으면 블록 수정 API는 인증 오류를 반환한다")
    void updateBlockReturnsUnauthorizedWhenHeaderMissing() throws Exception {
        var result = mockMvc.perform(patch("/v1/blocks/{blockId}", UUID.randomUUID())
                        .contentType("application/json")
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
                                """));

        ApiResponseAssertions.assertErrorEnvelope(result, "UNAUTHORIZED", 9001, "인증 정보가 없습니다.");
    }

    @Test
    @DisplayName("실패_version이 없으면 유효성 검사 오류를 반환한다")
    void updateBlockRejectsMissingVersion() throws Exception {
        var result = mockMvc.perform(patch("/v1/blocks/{blockId}", UUID.randomUUID())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
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
                                  }
                                }
                                """));

        ApiResponseAssertions.assertErrorEnvelope(result, "BAD_REQUEST", 9016, "요청 필드 유효성 검사에 실패했습니다.");
    }

    @Test
    @DisplayName("실패_version이 현재 블록 상태와 충돌하면 충돌 응답을 반환한다")
    void updateBlockReturnsConflictWhenVersionMismatched() throws Exception {
        UUID blockId = UUID.randomUUID();

        when(blockService.update(eq(blockId), eq(UPDATED_CONTENT_SERIALIZED), eq(0), eq("user-123")))
                .thenThrow(new BusinessException(BusinessErrorCode.CONFLICT));

        var result = mockMvc.perform(patch("/v1/blocks/{blockId}", blockId)
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
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
                                """));

        ApiResponseAssertions.assertErrorEnvelope(result, "CONFLICT", 9005, "요청이 현재 리소스 상태와 충돌합니다.");
    }

    private Block block(UUID blockId, UUID documentId, UUID parentId, String sortKey, int version, String text) {
        Block block = Block.builder()
                .id(blockId)
                .document(document(documentId))
                .parent(parentId == null ? null : parentBlock(parentId, documentId))
                .type(BlockType.TEXT)
                .content(toContent(text))
                .sortKey(sortKey)
                .createdBy("user-123")
                .updatedBy("user-123")
                .build();
        block.setVersion(version);
        block.setCreatedAt(LocalDateTime.of(2026, 3, 17, 0, 0));
        block.setUpdatedAt(LocalDateTime.of(2026, 3, 17, 0, 0));
        return block;
    }

    private Document document(UUID documentId) {
        return Document.builder()
                .id(documentId)
                .workspace(Workspace.builder()
                        .id(UUID.randomUUID())
                        .name("Docs Root")
                        .build())
                .title("문서")
                .sortKey("00000000000000000001")
                .build();
    }

    private Block parentBlock(UUID blockId, UUID documentId) {
        return Block.builder()
                .id(blockId)
                .document(document(documentId))
                .type(BlockType.TEXT)
                .content(toContent("부모 블록"))
                .sortKey("000000000001000000000000")
                .build();
    }

    private String toContent(String text) {
        return "{\"format\":\"rich_text\",\"schemaVersion\":1,\"segments\":[{\"text\":\"%s\",\"marks\":[]}]}".formatted(text);
    }
}
