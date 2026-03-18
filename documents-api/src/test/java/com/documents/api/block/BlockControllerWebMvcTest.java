package com.documents.api.block;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.documents.api.exception.GlobalExceptionHandler;
import com.documents.api.support.ApiResponseAssertions;
import com.documents.domain.Block;
import com.documents.domain.BlockType;
import com.documents.domain.Document;
import com.documents.domain.Workspace;
import com.documents.exception.BusinessErrorCode;
import com.documents.exception.BusinessException;
import com.documents.service.BlockService;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("Block 컨트롤러 빠른 검증")
class BlockControllerWebMvcTest {

    @Mock
    private BlockService blockService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new BlockController(blockService, new BlockApiMapper()))
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
                .andExpect(jsonPath("$.data[1].parentId").value(rootBlockId.toString()))
                .andExpect(jsonPath("$.data[1].text").value("자식 블록"));
    }

    @Test
    @DisplayName("성공_블록 생성 요청에 대해 gap 기반 sortKey 전략의 생성 응답을 반환한다")
    void createBlockReturnsCreatedEnvelope() throws Exception {
        UUID documentId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();

        when(blockService.create(
                eq(documentId),
                eq(parentId),
                eq(BlockType.TEXT),
                eq("새 블록"),
                eq(null),
                eq(null),
                eq("user-123")
        )).thenReturn(block(
                blockId,
                documentId,
                parentId,
                "000000000001000000000000",
                0,
                "새 블록"
        ));

        mockMvc.perform(post("/v1/documents/{documentId}/blocks", documentId)
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "parentId": "%s",
                                  "type": "TEXT",
                                  "text": "새 블록"
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
                .andExpect(jsonPath("$.data.text").value("새 블록"))
                .andExpect(jsonPath("$.data.sortKey").value("000000000001000000000000"))
                .andExpect(jsonPath("$.data.createdBy").value("user-123"))
                .andExpect(jsonPath("$.data.version").value(0));
    }

    @Test
    @DisplayName("실패_type이 없으면 유효성 검사 오류를 반환한다")
    void createBlockRejectsMissingType() throws Exception {
        var result = mockMvc.perform(post("/v1/documents/{documentId}/blocks", UUID.randomUUID())
                        .contentType("application/json")
                        .header("X-User-Id", "user-123")
                        .content("""
                                {
                                  "text": "새 블록"
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
                eq("새 블록"),
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
                                  "text": "새 블록"
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
                                  "text": "새 블록"
                                }
                                """));

        ApiResponseAssertions.assertErrorEnvelope(result, "UNAUTHORIZED", 9001, "인증 정보가 없습니다.");
    }

    private Block block(UUID blockId, UUID documentId, UUID parentId, String sortKey, int version, String text) {
        Block block = Block.builder()
                .id(blockId)
                .document(document(documentId))
                .parent(parentId == null ? null : parentBlock(parentId, documentId))
                .type(BlockType.TEXT)
                .text(text)
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
                .text("부모 블록")
                .sortKey("000000000001000000000000")
                .build();
    }
}
