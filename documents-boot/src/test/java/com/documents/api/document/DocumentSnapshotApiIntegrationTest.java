package com.documents.api.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.UUID;

import com.documents.api.block.JsonTestUtils;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("DocumentSnapshot API 통합 검증")
class DocumentSnapshotApiIntegrationTest {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ID = "user-123";

    private final ObjectMapper objectMapper = new ObjectMapper();

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
    @DisplayName("성공_문서 스냅샷 생성과 조회와 다운로드와 삭제는 platform resource를 사용한다")
    void createDescribeDownloadAndDeleteSnapshot() throws Exception {
        Document document = saveDocument(USER_ID, "스냅샷 문서");
        saveBlock(document.getId(), null, "첫 번째 블록", "000000000001000000000000");
        saveBlock(document.getId(), null, "두 번째 블록", "000000000002000000000000");

        String responseBody = mockMvc.perform(
                post("/documents/{documentId}/snapshots", document.getId())
                    .header(USER_ID_HEADER, USER_ID)
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.documentId").value(document.getId().toString()))
            .andExpect(jsonPath("$.data.documentVersion").value(0))
            .andExpect(jsonPath("$.data.contentType").value("application/json"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String snapshotId = JsonTestUtils.readString(responseBody, "$.data.snapshotId");

        mockMvc.perform(
                get("/documents/{documentId}/snapshots/{snapshotId}", document.getId(), snapshotId)
                    .header(USER_ID_HEADER, USER_ID)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.snapshotId").value(snapshotId))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        DocumentResource savedResource = documentResourceRepository
            .findByDocumentIdAndResourceIdAndUsageTypeAndStatus(
                document.getId(),
                snapshotId,
                DocumentResourceUsageType.DOCUMENT_SNAPSHOT,
                DocumentResourceStatus.ACTIVE
            )
            .orElseThrow();
        assertThat(savedResource.getResourceKind()).isEqualTo("document-snapshot");
        assertThat(savedResource.getOwnerUserId()).isEqualTo(USER_ID);
        assertThat(savedResource.getDocumentVersion()).isEqualTo(0L);
        assertThat(savedResource.getDeletedAt()).isNull();
        assertThat(savedResource.getStatus()).isEqualTo(DocumentResourceStatus.ACTIVE);

        MvcResult downloadResult = mockMvc.perform(
                get("/documents/{documentId}/snapshots/{snapshotId}/content", document.getId(), snapshotId)
                    .header(USER_ID_HEADER, USER_ID)
            )
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/json"))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("snapshot.json")))
            .andExpect(content().contentType("application/json"))
            .andReturn();

        JsonNode snapshotBody = objectMapper.readTree(downloadResult.getResponse().getContentAsByteArray());
        assertThat(snapshotBody.path("document").path("id").asText()).isEqualTo(document.getId().toString());
        assertThat(snapshotBody.path("document").path("title").asText()).isEqualTo("스냅샷 문서");
        assertThat(snapshotBody.path("blocks").size()).isEqualTo(2);
        assertThat(snapshotBody.path("blocks").get(0).path("type").asText()).isEqualTo("TEXT");

        mockMvc.perform(
                delete("/documents/{documentId}/snapshots/{snapshotId}", document.getId(), snapshotId)
                    .header(USER_ID_HEADER, USER_ID)
            )
            .andExpect(status().isOk());

        mockMvc.perform(
                get("/documents/{documentId}/snapshots/{snapshotId}", document.getId(), snapshotId)
                    .header(USER_ID_HEADER, USER_ID)
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(9009))
            .andExpect(jsonPath("$.message").value("요청한 문서 스냅샷을 찾을 수 없습니다."));

        DocumentResource deletedResource = documentResourceRepository.findById(savedResource.getId()).orElseThrow();
        assertThat(deletedResource.getDeletedAt()).isNotNull();
        assertThat(deletedResource.getStatus()).isEqualTo(DocumentResourceStatus.PENDING_PURGE);
        assertThat(deletedResource.getPurgeAt()).isNotNull();
    }

    @Test
    @DisplayName("실패_다른 문서 경로로 문서 스냅샷 조회하면 스냅샷 없음 응답을 반환한다")
    void getSnapshotRejectsMismatchedDocumentPath() throws Exception {
        Document sourceDocument = saveDocument(USER_ID, "원본 문서");
        Document otherDocument = saveDocument(USER_ID, "다른 문서");

        String responseBody = mockMvc.perform(
                post("/documents/{documentId}/snapshots", sourceDocument.getId())
                    .header(USER_ID_HEADER, USER_ID)
            )
            .andReturn()
            .getResponse()
            .getContentAsString();

        String snapshotId = JsonTestUtils.readString(responseBody, "$.data.snapshotId");

        mockMvc.perform(
                get("/documents/{documentId}/snapshots/{snapshotId}", otherDocument.getId(), snapshotId)
                    .header(USER_ID_HEADER, USER_ID)
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(9009))
            .andExpect(jsonPath("$.message").value("요청한 문서 스냅샷을 찾을 수 없습니다."));
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

    private Block saveBlock(UUID documentId, UUID parentId, String text, String sortKey) {
        Document document = documentRepository.findById(documentId).orElseThrow();
        Block parent = parentId == null ? null : blockRepository.findById(parentId).orElseThrow();
        return blockRepository.save(Block.builder()
            .id(UUID.randomUUID())
            .document(document)
            .parent(parent)
            .type(BlockType.TEXT)
            .content("{\"format\":\"rich_text\",\"schemaVersion\":1,\"segments\":[{\"text\":\"%s\",\"marks\":[]}]}".formatted(text))
            .sortKey(sortKey)
            .createdBy(USER_ID)
            .updatedBy(USER_ID)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build());
    }
}
