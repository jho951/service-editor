package com.documents.api.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.UUID;

import com.documents.api.block.JsonTestUtils;
import com.documents.boot.DocumentsResourceLifecycleRelay;
import com.documents.boot.DocumentsResourcePurgeScheduler;
import com.documents.domain.Block;
import com.documents.domain.BlockType;
import com.documents.domain.Document;
import com.documents.domain.DocumentResource;
import com.documents.domain.DocumentResourceStatus;
import com.documents.domain.DocumentVisibility;
import com.documents.repository.BlockRepository;
import com.documents.repository.DocumentResourceRepository;
import com.documents.repository.DocumentRepository;
import io.github.jho951.platform.resource.spi.ResourceLifecycleOutbox;
import io.github.jho951.platform.resource.spi.ResourceCatalog;
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
@DisplayName("Resource 접근/라이프사이클 통합 검증")
class ResourceAccessAndLifecycleIntegrationTest {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ID = "user-123";
    private static final String OTHER_USER_ID = "user-999";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private ResourceLifecycleOutbox resourceLifecycleOutbox;

    @Autowired
    private DocumentsResourceLifecycleRelay documentsResourceLifecycleRelay;

    @Autowired
    private DocumentResourceRepository documentResourceRepository;

    @Autowired
    private ResourceCatalog resourceCatalog;

    @Autowired
    private DocumentsResourcePurgeScheduler documentsResourcePurgeScheduler;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .defaultRequest(get("/").header(USER_ID_HEADER, USER_ID))
            .build();
        blockRepository.deleteAll();
        documentRepository.deleteAll();
        documentsResourceLifecycleRelay.relay();
    }

    @Test
    @DisplayName("실패_다른 사용자가 남의 문서 스냅샷 생성을 요청하면 권한 없음 응답을 반환한다")
    void createSnapshotRejectsOtherUser() throws Exception {
        Document document = saveDocument(USER_ID, "스냅샷 문서");

        mockMvc.perform(post("/documents/{documentId}/snapshots", document.getId()).header(USER_ID_HEADER, OTHER_USER_ID))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(9003))
            .andExpect(jsonPath("$.message").value("요청을 수행할 권한이 없습니다."));
    }

    @Test
    @DisplayName("실패_다른 사용자가 남의 블록 첨부 업로드를 요청하면 권한 없음 응답을 반환한다")
    void uploadAttachmentRejectsOtherUser() throws Exception {
        Document document = saveDocument(USER_ID, "첨부 문서");
        Block block = saveBlock(document, "블록");
        MockMultipartFile file = new MockMultipartFile("file", "diagram.png", "image/png", "png-content".getBytes());

        mockMvc.perform(
                multipart("/blocks/{blockId}/attachments", block.getId())
                    .file(file)
                    .header(USER_ID_HEADER, OTHER_USER_ID)
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(9003))
            .andExpect(jsonPath("$.message").value("요청을 수행할 권한이 없습니다."));
    }

    @Test
    @DisplayName("성공_attachment 저장 후 outbox relay를 실행하면 pending 이벤트가 비워진다")
    void relayPublishesPendingLifecycleEvents() throws Exception {
        Document document = saveDocument(USER_ID, "첨부 문서");
        Block block = saveBlock(document, "블록");
        MockMultipartFile file = new MockMultipartFile("file", "diagram.png", "image/png", "png-content".getBytes());

        mockMvc.perform(
                multipart("/blocks/{blockId}/attachments", block.getId())
                    .file(file)
                    .header(USER_ID_HEADER, USER_ID)
            )
            .andExpect(status().isCreated());

        documentsResourceLifecycleRelay.relay();

        assertThat(resourceLifecycleOutbox.pending(100)).isEmpty();
    }

    @Test
    @DisplayName("성공_문서를 휴지통으로 보내고 복구하면 attachment binding 상태가 TRASHED에서 ACTIVE로 돌아온다")
    void trashAndRestoreDocumentKeepsAttachmentBinding() throws Exception {
        Document document = saveDocument(USER_ID, "첨부 문서");
        Block block = saveBlock(document, "블록");
        MockMultipartFile file = new MockMultipartFile("file", "diagram.png", "image/png", "png-content".getBytes());

        String responseBody = mockMvc.perform(
                multipart("/blocks/{blockId}/attachments", block.getId())
                    .file(file)
                    .header(USER_ID_HEADER, USER_ID)
            )
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        String attachmentId = JsonTestUtils.readString(responseBody, "$.data.attachmentId");

        mockMvc.perform(patch("/documents/{documentId}/trash", document.getId()).header(USER_ID_HEADER, USER_ID))
            .andExpect(status().isOk());

        DocumentResource trashedBinding = documentResourceRepository.findByResourceId(attachmentId).orElseThrow();
        assertThat(trashedBinding.getStatus()).isEqualTo(DocumentResourceStatus.TRASHED);

        mockMvc.perform(post("/documents/{documentId}/restore", document.getId()).header(USER_ID_HEADER, USER_ID))
            .andExpect(status().isOk());

        DocumentResource restoredBinding = documentResourceRepository.findByResourceId(attachmentId).orElseThrow();
        assertThat(restoredBinding.getStatus()).isEqualTo(DocumentResourceStatus.ACTIVE);
    }

    @Test
    @DisplayName("성공_문서 hard delete 후 purge scheduler를 실행하면 attachment binding이 PURGED로 전이된다")
    void deleteDocumentSchedulesAndPurgesAttachmentBinding() throws Exception {
        Document document = saveDocument(USER_ID, "첨부 문서");
        Block block = saveBlock(document, "블록");
        MockMultipartFile file = new MockMultipartFile("file", "diagram.png", "image/png", "png-content".getBytes());

        String responseBody = mockMvc.perform(
                multipart("/blocks/{blockId}/attachments", block.getId())
                    .file(file)
                    .header(USER_ID_HEADER, USER_ID)
            )
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        String attachmentId = JsonTestUtils.readString(responseBody, "$.data.attachmentId");

        mockMvc.perform(delete("/documents/{documentId}", document.getId()).header(USER_ID_HEADER, USER_ID))
            .andExpect(status().isOk());

        DocumentResource pendingBinding = documentResourceRepository.findByResourceId(attachmentId).orElseThrow();
        assertThat(pendingBinding.getStatus()).isEqualTo(DocumentResourceStatus.PENDING_PURGE);
        assertThat(pendingBinding.getPurgeAt()).isNotNull();

        pendingBinding.setPurgeAt(LocalDateTime.now().minusSeconds(1));
        documentResourceRepository.save(pendingBinding);

        documentsResourcePurgeScheduler.purgePendingBindings();

        DocumentResource purgedBinding = documentResourceRepository.findByResourceId(attachmentId).orElseThrow();
        assertThat(purgedBinding.getStatus()).isEqualTo(DocumentResourceStatus.PURGED);
        assertThat(resourceCatalog.find(new io.github.jho951.platform.resource.api.ResourceId(attachmentId))).isEmpty();
    }

    private Document saveDocument(String ownerId, String title) {
        return documentRepository.save(Document.builder()
            .id(UUID.randomUUID())
            .title(title)
            .sortKey("00000000000000000001")
            .visibility(DocumentVisibility.PRIVATE)
            .createdBy(ownerId)
            .updatedBy(ownerId)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
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
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build());
    }
}
