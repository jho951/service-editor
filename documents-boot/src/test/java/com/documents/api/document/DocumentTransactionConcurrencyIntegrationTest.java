package com.documents.api.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.documents.domain.Block;
import com.documents.domain.BlockType;
import com.documents.domain.Document;
import com.documents.domain.Workspace;
import com.documents.repository.BlockRepository;
import com.documents.repository.DocumentRepository;
import com.documents.repository.WorkspaceRepository;
import com.documents.service.BlockServiceImpl;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Document transaction 동시성 통합 검증")
class DocumentTransactionConcurrencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private BlockRepository blockRepository;

    @MockitoSpyBean
    private BlockServiceImpl blockService;

    @BeforeEach
    void setUp() {
        blockRepository.deleteAll();
        documentRepository.deleteAll();
        workspaceRepository.deleteAll();
        Mockito.reset(blockService);
    }

    @AfterEach
    void tearDown() {
        Mockito.reset(blockService);
    }

    @Test
    @DisplayName("동시성_같은 block replace_content 요청 2개가 동시에 오면 하나만 성공하고 나머지는 충돌한다")
    void concurrentReplaceContentOnlyOneSucceeds() throws Exception {
        Document document = document("문서");
        Block block = block(document, null, "기존 블록", "000000000001000000000000");

        serializeConcurrentUpdates(2);

        Future<MvcResult> first = submitTransaction(document.getId(), replaceContentRequest("batch-a", block.getId(), 0, "첫 번째 수정"));
        Future<MvcResult> second = submitTransaction(document.getId(), replaceContentRequest("batch-b", block.getId(), 0, "두 번째 수정"));

        List<Integer> statuses = List.of(first.get().getResponse().getStatus(), second.get().getResponse().getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);

        Block reloadedBlock = blockRepository.findByIdAndDeletedAtIsNull(block.getId()).orElseThrow();
        assertThat(reloadedBlock.getVersion()).isEqualTo(1);
        assertThat(reloadedBlock.getContent()).isIn(content("첫 번째 수정"), content("두 번째 수정"));
    }

    @Test
    @DisplayName("동시성_같은 block move 요청 2개가 동시에 오면 하나만 성공하고 나머지는 충돌한다")
    void concurrentMoveOnlyOneSucceeds() throws Exception {
        Document document = document("문서");
        Block targetParent = block(document, null, "대상 부모", "000000000001000000000000");
        Block movingBlock = block(document, null, "이동 대상", "000000000002000000000000");

        serializeConcurrentMoves(2);

        String requestBody = moveRequest("batch-move", movingBlock.getId(), 0, targetParent.getId());
        Future<MvcResult> first = submitTransaction(document.getId(), requestBody);
        Future<MvcResult> second = submitTransaction(document.getId(), requestBody.replace("batch-move", "batch-move-2"));

        List<Integer> statuses = List.of(first.get().getResponse().getStatus(), second.get().getResponse().getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);

        Block reloadedBlock = blockRepository.findByIdAndDeletedAtIsNull(movingBlock.getId()).orElseThrow();
        assertThat(reloadedBlock.getVersion()).isEqualTo(1);
        assertThat(reloadedBlock.getParentId()).isEqualTo(targetParent.getId());
    }

    @Test
    @DisplayName("동시성_같은 block delete 요청 2개가 동시에 오면 하나만 성공하고 나머지는 실패한다")
    void concurrentDeleteOnlyOneSucceeds() throws Exception {
        Document document = document("문서");
        Block rootBlock = block(document, null, "삭제 대상", "000000000001000000000000");

        serializeConcurrentDeletes(2);

        String requestBody = deleteRequest("batch-delete", rootBlock.getId(), 0);
        Future<MvcResult> first = submitTransaction(document.getId(), requestBody);
        Future<MvcResult> second = submitTransaction(document.getId(), requestBody.replace("batch-delete", "batch-delete-2"));

        List<Integer> statuses = List.of(first.get().getResponse().getStatus(), second.get().getResponse().getStatus());
        assertOneSucceededAndOtherFailed(statuses);

        Block deletedBlock = blockRepository.findById(rootBlock.getId()).orElseThrow();
        assertThat(deletedBlock.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("동시성_같은 block에 대한 replace_content와 move가 동시에 오면 하나만 성공하고 나머지는 충돌한다")
    void concurrentReplaceAndMoveOnlyOneSucceeds() throws Exception {
        Document document = document("문서");
        Block targetParent = block(document, null, "대상 부모", "000000000001000000000000");
        Block block = block(document, null, "기존 블록", "000000000002000000000000");

        serializeConcurrentUpdateAndMove(2);

        Future<MvcResult> replace = submitTransaction(
                document.getId(),
                replaceContentRequest("batch-replace", block.getId(), 0, "동시 수정")
        );
        Future<MvcResult> move = submitTransaction(
                document.getId(),
                moveRequest("batch-move", block.getId(), 0, targetParent.getId())
        );

        List<Integer> statuses = List.of(replace.get().getResponse().getStatus(), move.get().getResponse().getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);

        Block reloadedBlock = blockRepository.findByIdAndDeletedAtIsNull(block.getId()).orElseThrow();
        assertThat(reloadedBlock.getVersion()).isEqualTo(1);
        assertThat(reloadedBlock.getContent().contains("\"text\":\"동시 수정\"")
                || targetParent.getId().equals(reloadedBlock.getParentId())).isTrue();
    }

    @Test
    @DisplayName("동시성_같은 block에 대한 batch replace->move와 단건 replace가 동시에 오면 하나만 성공한다")
    void concurrentBatchChainAndSingleReplaceOnlyOneSucceeds() throws Exception {
        Document document = document("문서");
        Block targetParent = block(document, null, "대상 부모", "000000000001000000000000");
        Block block = block(document, null, "기존 블록", "000000000002000000000000");

        serializeConcurrentUpdates(2);

        Future<MvcResult> chainedBatch = submitTransaction(
                document.getId(),
                """
                        {
                          "clientId": "web-editor",
                          "documentVersion": 0,
                          "batchId": "batch-chain",
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
                                    "text": "체인 수정",
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
                        """.formatted(block.getId(), block.getId(), targetParent.getId())
        );

        Future<MvcResult> singleReplace = submitTransaction(
                document.getId(),
                replaceContentRequest("batch-single", block.getId(), 0, "단일 수정")
        );

        List<Integer> statuses = List.of(chainedBatch.get().getResponse().getStatus(), singleReplace.get().getResponse().getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);

        Block reloadedBlock = blockRepository.findByIdAndDeletedAtIsNull(block.getId()).orElseThrow();
        assertThat(reloadedBlock.getVersion()).isIn(1, 2);
    }

    @Test
    @DisplayName("동시성_같은 block에 대한 replace_content와 delete가 동시에 오면 하나만 성공하고 block 상태가 되살아나지 않는다")
    void concurrentReplaceAndDeleteOnlyOneSucceedsWithoutRevivingDeletedBlock() throws Exception {
        Document document = document("문서");
        Block block = block(document, null, "기존 블록", "000000000001000000000000");

        serializeConcurrentUpdateAndDelete(2);

        Future<MvcResult> replace = submitTransaction(
                document.getId(),
                replaceContentRequest("batch-replace", block.getId(), 0, "동시 수정")
        );
        Future<MvcResult> delete = submitTransaction(
                document.getId(),
                deleteRequest("batch-delete", block.getId(), 0)
        );

        List<Integer> statuses = List.of(replace.get().getResponse().getStatus(), delete.get().getResponse().getStatus());
        assertOneSucceededAndOtherFailed(statuses);

        Block reloadedBlock = blockRepository.findById(block.getId()).orElseThrow();
        if (reloadedBlock.getDeletedAt() == null) {
            assertThat(reloadedBlock.getVersion()).isEqualTo(1);
            assertThat(reloadedBlock.getContent()).contains("\"text\":\"동시 수정\"");
            return;
        }

        assertThat(reloadedBlock.getVersion()).isEqualTo(1);
        assertThat(reloadedBlock.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("동시성_같은 block에 대한 move와 delete가 동시에 오면 하나만 성공하고 block 상태가 되살아나지 않는다")
    void concurrentMoveAndDeleteOnlyOneSucceedsWithoutRevivingDeletedBlock() throws Exception {
        Document document = document("문서");
        Block targetParent = block(document, null, "대상 부모", "000000000001000000000000");
        Block movingBlock = block(document, null, "이동 대상", "000000000002000000000000");

        serializeConcurrentMoveAndDelete(2);

        Future<MvcResult> move = submitTransaction(
                document.getId(),
                moveRequest("batch-move", movingBlock.getId(), 0, targetParent.getId())
        );
        Future<MvcResult> delete = submitTransaction(
                document.getId(),
                deleteRequest("batch-delete", movingBlock.getId(), 0)
        );

        List<Integer> statuses = List.of(move.get().getResponse().getStatus(), delete.get().getResponse().getStatus());
        assertOneSucceededAndOtherFailed(statuses);

        Block reloadedBlock = blockRepository.findById(movingBlock.getId()).orElseThrow();
        if (reloadedBlock.getDeletedAt() == null) {
            assertThat(reloadedBlock.getVersion()).isEqualTo(1);
            assertThat(reloadedBlock.getParentId()).isEqualTo(targetParent.getId());
            return;
        }

        assertThat(reloadedBlock.getVersion()).isEqualTo(1);
        assertThat(reloadedBlock.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("동시성_서로 다른 block replace_content 요청도 같은 documentVersion이면 하나만 성공하고 나머지는 충돌한다")
    void concurrentReplaceOnDifferentBlocksOnlyOneSucceedsBecauseDocumentVersionConflicts() throws Exception {
        Document document = document("문서");
        Block firstBlock = block(document, null, "첫 번째 블록", "000000000001000000000000");
        Block secondBlock = block(document, null, "두 번째 블록", "000000000002000000000000");

        serializeConcurrentUpdates(2);

        Future<MvcResult> first = submitTransaction(
                document.getId(),
                replaceContentRequest("batch-first", firstBlock.getId(), 0, "첫 번째 수정")
        );
        Future<MvcResult> second = submitTransaction(
                document.getId(),
                replaceContentRequest("batch-second", secondBlock.getId(), 0, "두 번째 수정")
        );

        List<Integer> statuses = List.of(first.get().getResponse().getStatus(), second.get().getResponse().getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);

        Block reloadedFirstBlock = blockRepository.findByIdAndDeletedAtIsNull(firstBlock.getId()).orElseThrow();
        Block reloadedSecondBlock = blockRepository.findByIdAndDeletedAtIsNull(secondBlock.getId()).orElseThrow();
        assertThat(reloadedFirstBlock.getVersion() + reloadedSecondBlock.getVersion()).isEqualTo(1);
        assertThat(reloadedFirstBlock.getContent().contains("\"text\":\"첫 번째 수정\"")
                || reloadedSecondBlock.getContent().contains("\"text\":\"두 번째 수정\"")).isTrue();
    }

    private void serializeConcurrentUpdates(int participants) throws Exception {
        Answer<Object> answer = serializingAnswer(participants);
        Mockito.doAnswer(answer)
                .when(blockService)
                .update(Mockito.any(UUID.class), Mockito.anyString(), Mockito.anyInt(), Mockito.anyString());
    }

    private void serializeConcurrentMoves(int participants) throws Exception {
        Answer<Object> answer = serializingAnswer(participants);
        Mockito.doAnswer(answer)
                .when(blockService)
                .move(Mockito.any(UUID.class), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyInt(), Mockito.anyString());
    }

    private void serializeConcurrentDeletes(int participants) throws Exception {
        Answer<Object> answer = serializingAnswer(participants);
        Mockito.doAnswer(answer)
                .when(blockService)
                .delete(Mockito.any(UUID.class), Mockito.anyInt(), Mockito.anyString());
    }

    private void serializeConcurrentUpdateAndMove(int participants) throws Exception {
        Answer<Object> answer = serializingAnswer(participants);
        Mockito.doAnswer(answer)
                .when(blockService)
                .update(Mockito.any(UUID.class), Mockito.anyString(), Mockito.anyInt(), Mockito.anyString());
        Mockito.doAnswer(answer)
                .when(blockService)
                .move(Mockito.any(UUID.class), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyInt(), Mockito.anyString());
    }

    private void serializeConcurrentUpdateAndDelete(int participants) throws Exception {
        Answer<Object> answer = serializingAnswer(participants);
        Mockito.doAnswer(answer)
                .when(blockService)
                .update(Mockito.any(UUID.class), Mockito.anyString(), Mockito.anyInt(), Mockito.anyString());
        Mockito.doAnswer(answer)
                .when(blockService)
                .delete(Mockito.any(UUID.class), Mockito.anyInt(), Mockito.anyString());
    }

    private void serializeConcurrentMoveAndDelete(int participants) throws Exception {
        Answer<Object> answer = serializingAnswer(participants);
        Mockito.doAnswer(answer)
                .when(blockService)
                .move(Mockito.any(UUID.class), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyInt(), Mockito.anyString());
        Mockito.doAnswer(answer)
                .when(blockService)
                .delete(Mockito.any(UUID.class), Mockito.anyInt(), Mockito.anyString());
    }

    private Answer<Object> serializingAnswer(int participants) {
        CountDownLatch entered = new CountDownLatch(participants);
        CountDownLatch firstCompleted = new CountDownLatch(1);
        AtomicInteger order = new AtomicInteger(0);

        return invocation -> {
            entered.countDown();
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

            int currentOrder = order.incrementAndGet();
            if (currentOrder == 1) {
                try {
                    return invocation.callRealMethod();
                } finally {
                    firstCompleted.countDown();
                }
            }

            assertThat(firstCompleted.await(5, TimeUnit.SECONDS)).isTrue();
            return invocation.callRealMethod();
        };
    }

    private Future<MvcResult> submitTransaction(UUID documentId, String requestBody) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<MvcResult> future = executor.submit(() -> mockMvc.perform(post("/v1/documents/{documentId}/transactions", documentId)
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content(requestBody))
                .andReturn());
        executor.shutdown();
        return future;
    }

    private String replaceContentRequest(String batchId, UUID blockId, int version, String text) {
        return """
                {
                  "clientId": "web-editor",
                  "documentVersion": 0,
                  "batchId": "%s",
                  "operations": [
                    {
                      "opId": "op-1",
                      "type": "BLOCK_REPLACE_CONTENT",
                      "blockRef": "%s",
                      "version": %d,
                      "content": {
                        "format": "rich_text",
                        "schemaVersion": 1,
                        "segments": [
                          {
                            "text": "%s",
                            "marks": []
                          }
                        ]
                      }
                    }
                  ]
                }
                """.formatted(batchId, blockId, version, text);
    }

    private String moveRequest(String batchId, UUID blockId, int version, UUID parentId) {
        return """
                {
                  "clientId": "web-editor",
                  "documentVersion": 0,
                  "batchId": "%s",
                  "operations": [
                    {
                      "opId": "op-1",
                      "type": "BLOCK_MOVE",
                      "blockRef": "%s",
                      "version": %d,
                      "parentRef": "%s"
                    }
                  ]
                }
                """.formatted(batchId, blockId, version, parentId);
    }

    private String deleteRequest(String batchId, UUID blockId, int version) {
        return """
                {
                  "clientId": "web-editor",
                  "documentVersion": 0,
                  "batchId": "%s",
                  "operations": [
                    {
                      "opId": "op-1",
                      "type": "BLOCK_DELETE",
                      "blockRef": "%s",
                      "version": %d
                    }
                  ]
                }
                """.formatted(batchId, blockId, version);
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
        Block block = Block.builder()
                .id(UUID.randomUUID())
                .document(document)
                .parent(parent)
                .type(BlockType.TEXT)
                .content(content(text))
                .sortKey(sortKey)
                .createdBy("user-123")
                .updatedBy("user-123")
                .build();
        return blockRepository.save(block);
    }

    private String content(String text) {
        return "{\"format\":\"rich_text\",\"schemaVersion\":1,\"segments\":[{\"text\":\"%s\",\"marks\":[]}]}".formatted(text);
    }

    private void assertOneSucceededAndOtherFailed(List<Integer> statuses) {
        long successCount = statuses.stream().filter(status -> status == 200).count();
        long failureCount = statuses.stream().filter(status -> status == 404 || status == 409).count();

        assertThat(successCount).isEqualTo(1);
        assertThat(failureCount).isEqualTo(1);
    }
}
