package com.documents.api.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.documents.domain.Block;
import com.documents.domain.BlockType;
import com.documents.domain.Document;
import com.documents.domain.DocumentResource;
import com.documents.domain.DocumentResourceStatus;
import com.documents.domain.DocumentResourceUsageType;
import com.documents.domain.DocumentVisibility;
import com.documents.repository.BlockRepository;
import com.documents.repository.DocumentResourceRepository;
import com.documents.repository.DocumentRepository;
import com.documents.service.BlockAttachmentService;
import com.documents.service.attachment.BlockAttachmentDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("BlockAttachment API 통합 검증")
class BlockAttachmentApiIntegrationTest {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ID = "user-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private DocumentResourceRepository documentResourceRepository;

    @Autowired
    private BlockAttachmentService blockAttachmentService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .defaultRequest(get("/").header(USER_ID_HEADER, USER_ID))
            .build();
        documentResourceRepository.deleteAll();
        blockRepository.deleteAll();
        documentRepository.deleteAll();
    }

    @Test
    @DisplayName("성공_블록 첨부파일 업로드와 조회와 다운로드와 삭제는 platform resource를 사용한다")
    void uploadDescribeDownloadAndDeleteAttachment() throws Exception {
        Document document = saveDocument(USER_ID, "문서");
        Block block = saveBlock(document, "블록");
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "diagram.png",
            "image/png",
            "png-content".getBytes()
        );

        String responseBody = mockMvc.perform(
                multipart("/blocks/{blockId}/attachments", block.getId())
                    .file(file)
                    .header(USER_ID_HEADER, USER_ID)
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.blockId").value(block.getId().toString()))
            .andExpect(jsonPath("$.data.documentId").value(document.getId().toString()))
            .andExpect(jsonPath("$.data.originalName").value("diagram.png"))
            .andExpect(jsonPath("$.data.contentType").value("image/png"))
            .andExpect(jsonPath("$.data.size").value(file.getBytes().length))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String attachmentId = JsonTestUtils.readString(responseBody, "$.data.attachmentId");

        mockMvc.perform(
                get("/blocks/{blockId}/attachments/{attachmentId}", block.getId(), attachmentId)
                    .header(USER_ID_HEADER, USER_ID)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.attachmentId").value(attachmentId))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        DocumentResource savedResource = documentResourceRepository
            .findByDocumentIdAndBlockIdAndResourceIdAndUsageTypeAndStatus(
                document.getId(),
                block.getId(),
                attachmentId,
                DocumentResourceUsageType.BLOCK_ATTACHMENT,
                DocumentResourceStatus.ACTIVE
            )
            .orElseThrow();
        assertThat(savedResource.getResourceKind()).isEqualTo("editor-attachment");
        assertThat(savedResource.getOwnerUserId()).isEqualTo(USER_ID);
        assertThat(savedResource.getDeletedAt()).isNull();
        assertThat(savedResource.getStatus()).isEqualTo(DocumentResourceStatus.ACTIVE);

        mockMvc.perform(
                get("/blocks/{blockId}/attachments/{attachmentId}/content", block.getId(), attachmentId)
                    .header(USER_ID_HEADER, USER_ID)
            )
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "image/png"))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("diagram.png")))
            .andExpect(content().bytes(file.getBytes()));

        mockMvc.perform(
                delete("/blocks/{blockId}/attachments/{attachmentId}", block.getId(), attachmentId)
                    .header(USER_ID_HEADER, USER_ID)
            )
            .andExpect(status().isOk());

        mockMvc.perform(
                get("/blocks/{blockId}/attachments/{attachmentId}", block.getId(), attachmentId)
                    .header(USER_ID_HEADER, USER_ID)
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(9008))
            .andExpect(jsonPath("$.message").value("요청한 첨부파일을 찾을 수 없습니다."));

        DocumentResource deletedResource = documentResourceRepository.findById(savedResource.getId()).orElseThrow();
        assertThat(deletedResource.getDeletedAt()).isNotNull();
        assertThat(deletedResource.getStatus()).isEqualTo(DocumentResourceStatus.PENDING_PURGE);
        assertThat(deletedResource.getPurgeAt()).isNotNull();
    }

    @Test
    @DisplayName("실패_다른 블록 경로로 첨부파일 조회하면 첨부파일 없음 응답을 반환한다")
    void getAttachmentRejectsMismatchedBlockPath() throws Exception {
        Document document = saveDocument(USER_ID, "문서");
        Block sourceBlock = saveBlock(document, "원본 블록");
        Block otherBlock = saveBlock(document, "다른 블록");

        BlockAttachmentDescriptor stored = blockAttachmentService.store(
            sourceBlock.getId(),
            "diagram.png",
            "image/png",
            11L,
            new java.io.ByteArrayInputStream("png-content".getBytes()),
            USER_ID
        );

        mockMvc.perform(
                get("/blocks/{blockId}/attachments/{attachmentId}", otherBlock.getId(), stored.attachmentId())
                    .header(USER_ID_HEADER, USER_ID)
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(9008))
            .andExpect(jsonPath("$.message").value("요청한 첨부파일을 찾을 수 없습니다."));
    }

    private Document saveDocument(String ownerId, String title) {
        return documentRepository.save(Document.builder()
            .id(UUID.randomUUID())
            .title(title)
            .sortKey("00000000000000000001")
            .visibility(DocumentVisibility.PRIVATE)
            .createdBy(ownerId)
            .updatedBy(ownerId)
            .build());
    }

    private Block saveBlock(Document document, String text) {
        return blockRepository.save(Block.builder()
            .id(UUID.randomUUID())
            .document(document)
            .type(BlockType.TEXT)
            .content("{\"format\":\"rich_text\",\"schemaVersion\":1,\"segments\":[{\"text\":\"%s\",\"marks\":[]}]}".formatted(text))
            .sortKey(UUID.randomUUID().toString().replace("-", "").substring(0, 24))
            .createdBy(USER_ID)
            .updatedBy(USER_ID)
            .build());
    }
}
