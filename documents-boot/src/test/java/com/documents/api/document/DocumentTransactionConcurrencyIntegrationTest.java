package com.documents.api.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    @Autowired
    private ObjectMapper objectMapper;

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

    @Test
    @DisplayName("동시성_서로 다른 block move 요청도 같은 documentVersion이면 하나만 성공하고 나머지는 충돌한다")
    void concurrentMoveOnDifferentBlocksOnlyOneSucceedsBecauseDocumentVersionConflicts() throws Exception {
        Document document = document("문서");
        Block targetParent = block(document, null, "대상 부모", "000000000001000000000000");
        Block firstBlock = block(document, null, "첫 번째 이동 대상", "000000000002000000000000");
        Block secondBlock = block(document, null, "두 번째 이동 대상", "000000000003000000000000");

        serializeConcurrentMoves(2);

        Future<MvcResult> first = submitTransaction(
                document.getId(),
                moveRequest("batch-move-first", firstBlock.getId(), 0, targetParent.getId())
        );
        Future<MvcResult> second = submitTransaction(
                document.getId(),
                moveRequest("batch-move-second", secondBlock.getId(), 0, targetParent.getId())
        );

        List<Integer> statuses = List.of(first.get().getResponse().getStatus(), second.get().getResponse().getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);

        Block reloadedFirstBlock = blockRepository.findByIdAndDeletedAtIsNull(firstBlock.getId()).orElseThrow();
        Block reloadedSecondBlock = blockRepository.findByIdAndDeletedAtIsNull(secondBlock.getId()).orElseThrow();
        long movedCount = List.of(reloadedFirstBlock, reloadedSecondBlock).stream()
                .filter(block -> targetParent.getId().equals(block.getParentId()))
                .count();
        assertThat(movedCount).isEqualTo(1);
        assertThat(reloadedFirstBlock.getVersion() + reloadedSecondBlock.getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("동시성_서로 다른 block delete 요청도 같은 documentVersion이면 하나만 성공하고 나머지는 충돌한다")
    void concurrentDeleteOnDifferentBlocksOnlyOneSucceedsBecauseDocumentVersionConflicts() throws Exception {
        Document document = document("문서");
        Block firstBlock = block(document, null, "첫 번째 삭제 대상", "000000000001000000000000");
        Block secondBlock = block(document, null, "두 번째 삭제 대상", "000000000002000000000000");

        serializeConcurrentDeletes(2);

        Future<MvcResult> first = submitTransaction(
                document.getId(),
                deleteRequest("batch-delete-first", firstBlock.getId(), 0)
        );
        Future<MvcResult> second = submitTransaction(
                document.getId(),
                deleteRequest("batch-delete-second", secondBlock.getId(), 0)
        );

        List<Integer> statuses = List.of(first.get().getResponse().getStatus(), second.get().getResponse().getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);

        Block reloadedFirstBlock = blockRepository.findById(firstBlock.getId()).orElseThrow();
        Block reloadedSecondBlock = blockRepository.findById(secondBlock.getId()).orElseThrow();
        long deletedCount = List.of(reloadedFirstBlock, reloadedSecondBlock).stream()
                .filter(block -> block.getDeletedAt() != null)
                .count();
        assertThat(deletedCount).isEqualTo(1);
        assertThat(List.of(reloadedFirstBlock, reloadedSecondBlock).stream()
                .filter(block -> block.getDeletedAt() == null)
                .findFirst()
                .orElseThrow()
                .getVersion()).isEqualTo(0);
    }

    @Test
    @DisplayName("동시성_새 block create 요청 2개가 같은 documentVersion으로 동시에 오면 하나만 성공하고 나머지는 충돌한다")
    void concurrentCreateOnlyOneSucceedsBecauseDocumentVersionConflicts() throws Exception {
        Document document = document("문서");

        serializeConcurrentCreates(2);

        Future<MvcResult> first = submitTransaction(document.getId(), createRequest("batch-create-first", "tmp:block:first"));
        Future<MvcResult> second = submitTransaction(document.getId(), createRequest("batch-create-second", "tmp:block:second"));

        List<Integer> statuses = List.of(first.get().getResponse().getStatus(), second.get().getResponse().getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);

        List<Block> activeBlocks = blockRepository.findActiveByDocumentIdOrderBySortKey(document.getId());
        assertThat(activeBlocks).hasSize(1);
        assertThat(activeBlocks.get(0).getVersion()).isEqualTo(0);
    }

    @Test
    @DisplayName("동시성_새 block create와 기존 block replace_content가 같은 documentVersion으로 동시에 오면 하나만 성공한다")
    void concurrentCreateAndReplaceOnlyOneSucceedsBecauseDocumentVersionConflicts() throws Exception {
        Document document = document("문서");
        Block existingBlock = block(document, null, "기존 블록", "000000000001000000000000");

        serializeConcurrentCreateAndUpdate(2);

        Future<MvcResult> create = submitTransaction(
                document.getId(),
                createRequest("batch-create", "tmp:block:create")
        );
        Future<MvcResult> replace = submitTransaction(
                document.getId(),
                replaceContentRequest("batch-replace", existingBlock.getId(), 0, "동시 수정")
        );

        List<Integer> statuses = List.of(create.get().getResponse().getStatus(), replace.get().getResponse().getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);

        List<Block> activeBlocks = blockRepository.findActiveByDocumentIdOrderBySortKey(document.getId());
        assertThat(activeBlocks).hasSizeBetween(1, 2);

        Block reloadedExistingBlock = blockRepository.findByIdAndDeletedAtIsNull(existingBlock.getId()).orElseThrow();
        boolean createSucceeded = activeBlocks.size() == 2;
        boolean replaceSucceeded = reloadedExistingBlock.getVersion() == 1;
        assertThat(createSucceeded ^ replaceSucceeded).isTrue();
    }

    @Test
    @DisplayName("동시성_같은 block replace_content 요청 3개가 동시에 오면 하나만 성공하고 나머지는 충돌한다")
    void concurrentReplaceContentThreeWayOnlyOneSucceeds() throws Exception {
        Document document = document("문서");
        Block block = block(document, null, "기존 블록", "000000000001000000000000");

        serializeConcurrentUpdates(3);

        Future<MvcResult> first = submitTransaction(document.getId(), replaceContentRequest("batch-a", block.getId(), 0, "첫 번째 수정"));
        Future<MvcResult> second = submitTransaction(document.getId(), replaceContentRequest("batch-b", block.getId(), 0, "두 번째 수정"));
        Future<MvcResult> third = submitTransaction(document.getId(), replaceContentRequest("batch-c", block.getId(), 0, "세 번째 수정"));

        List<Integer> statuses = List.of(
                first.get().getResponse().getStatus(),
                second.get().getResponse().getStatus(),
                third.get().getResponse().getStatus()
        );
        assertThat(statuses.stream().filter(status -> status == 200).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(status -> status == 409).count()).isEqualTo(2);

        Block reloadedBlock = blockRepository.findByIdAndDeletedAtIsNull(block.getId()).orElseThrow();
        assertThat(reloadedBlock.getVersion()).isEqualTo(1);
        assertThat(reloadedBlock.getContent()).isIn(
                content("첫 번째 수정"),
                content("두 번째 수정"),
                content("세 번째 수정")
        );
    }

    @Test
    @DisplayName("동시성_서로 다른 block replace_content 요청 3개도 같은 documentVersion이면 하나만 성공한다")
    void concurrentReplaceOnDifferentBlocksThreeWayOnlyOneSucceedsBecauseDocumentVersionConflicts() throws Exception {
        Document document = document("문서");
        Block firstBlock = block(document, null, "첫 번째 블록", "000000000001000000000000");
        Block secondBlock = block(document, null, "두 번째 블록", "000000000002000000000000");
        Block thirdBlock = block(document, null, "세 번째 블록", "000000000003000000000000");

        serializeConcurrentUpdates(3);

        Future<MvcResult> first = submitTransaction(
                document.getId(),
                replaceContentRequest("batch-first", firstBlock.getId(), 0, "첫 번째 수정")
        );
        Future<MvcResult> second = submitTransaction(
                document.getId(),
                replaceContentRequest("batch-second", secondBlock.getId(), 0, "두 번째 수정")
        );
        Future<MvcResult> third = submitTransaction(
                document.getId(),
                replaceContentRequest("batch-third", thirdBlock.getId(), 0, "세 번째 수정")
        );

        List<Integer> statuses = List.of(
                first.get().getResponse().getStatus(),
                second.get().getResponse().getStatus(),
                third.get().getResponse().getStatus()
        );
        assertThat(statuses.stream().filter(status -> status == 200).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(status -> status == 409).count()).isEqualTo(2);

        List<Block> reloadedBlocks = List.of(
                blockRepository.findByIdAndDeletedAtIsNull(firstBlock.getId()).orElseThrow(),
                blockRepository.findByIdAndDeletedAtIsNull(secondBlock.getId()).orElseThrow(),
                blockRepository.findByIdAndDeletedAtIsNull(thirdBlock.getId()).orElseThrow()
        );
        assertThat(reloadedBlocks.stream().mapToInt(Block::getVersion).sum()).isEqualTo(1);
    }

    @Test
    @DisplayName("경쟁형_같은 block replace_content 요청 10개를 출발선만 맞춰 동시에 보내면 하나만 성공한다")
    void racingReplaceContentOnSameBlockTenRequestsOnlyOneSucceeds() throws Exception {
        Document document = document("문서");
        Block block = block(document, null, "기존 블록", "000000000001000000000000");

        List<String> requestBodies = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            requestBodies.add(replaceContentRequest("batch-race-" + index, block.getId(), 0, "수정-" + index));
        }

        List<Integer> statuses = submitTransactionsSimultaneously(document.getId(), requestBodies);

        assertThat(statuses.stream().filter(status -> status == 200).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(status -> status == 409).count()).isEqualTo(9);

        Block reloadedBlock = blockRepository.findByIdAndDeletedAtIsNull(block.getId()).orElseThrow();
        assertThat(reloadedBlock.getVersion()).isEqualTo(1);
        assertThat(reloadedBlock.getContent()).contains("\"text\":\"수정-");
    }

    @Test
    @DisplayName("경쟁형_같은 block replace_content 요청 10개에서 성공 응답은 documentVersion 1을 반환하고 실패 응답은 충돌 코드를 반환한다")
    void racingReplaceContentOnSameBlockTenRequestsReturnsExpectedResponseContract() throws Exception {
        Document document = document("문서");
        Block block = block(document, null, "기존 블록", "000000000001000000000000");

        List<String> requestBodies = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            requestBodies.add(replaceContentRequest("batch-race-" + index, block.getId(), 0, "수정-" + index));
        }

        List<MvcResult> results = submitTransactionsSimultaneouslyForResults(document.getId(), requestBodies);

        List<MvcResult> successResults = results.stream()
                .filter(result -> result.getResponse().getStatus() == 200)
                .toList();
        List<MvcResult> failureResults = results.stream()
                .filter(result -> result.getResponse().getStatus() == 409)
                .toList();

        assertThat(successResults).hasSize(1);
        assertThat(failureResults).hasSize(9);
        assertThat(extractDocumentVersion(successResults.get(0))).isEqualTo(1);
        assertThat(extractErrorCode(failureResults.get(0))).isEqualTo(9005);
    }

    @Test
    @DisplayName("경쟁형_서로 다른 block replace_content 요청 10개도 같은 documentVersion이면 하나만 성공한다")
    void racingReplaceContentOnDifferentBlocksTenRequestsOnlyOneSucceedsBecauseDocumentVersionConflicts() throws Exception {
        Document document = document("문서");

        List<Block> blocks = new ArrayList<>();
        List<String> requestBodies = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            Block block = block(document, null, "블록-" + index, "00000000000" + index + "000000000000");
            blocks.add(block);
            requestBodies.add(replaceContentRequest("batch-race-" + index, block.getId(), 0, "수정-" + index));
        }

        List<Integer> statuses = submitTransactionsSimultaneously(document.getId(), requestBodies);

        assertThat(statuses.stream().filter(status -> status == 200).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(status -> status == 409).count()).isEqualTo(9);

        int totalVersion = blocks.stream()
                .map(block -> blockRepository.findByIdAndDeletedAtIsNull(block.getId()).orElseThrow())
                .mapToInt(Block::getVersion)
                .sum();
        assertThat(totalVersion).isEqualTo(1);
    }

    @Test
    @DisplayName("경쟁형_새 block create 요청 10개를 같은 documentVersion으로 동시에 보내면 하나만 성공한다")
    void racingCreateTenRequestsOnlyOneSucceedsBecauseDocumentVersionConflicts() throws Exception {
        Document document = document("문서");

        List<String> requestBodies = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            requestBodies.add(createRequest("batch-create-race-" + index, "tmp:block:" + index));
        }

        List<Integer> statuses = submitTransactionsSimultaneously(document.getId(), requestBodies);

        assertThat(statuses.stream().filter(status -> status == 200).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(status -> status == 409).count()).isEqualTo(9);

        assertThat(blockRepository.findActiveByDocumentIdOrderBySortKey(document.getId())).hasSize(1);
    }

    @Test
    @DisplayName("경쟁형_같은 block move 요청 10개를 출발선만 맞춰 동시에 보내면 하나만 성공한다")
    void racingMoveOnSameBlockTenRequestsOnlyOneSucceeds() throws Exception {
        Document document = document("문서");
        Block targetParent = block(document, null, "대상 부모", "000000000001000000000000");
        Block movingBlock = block(document, null, "이동 대상", "000000000002000000000000");

        List<String> requestBodies = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            requestBodies.add(moveRequest("batch-move-race-" + index, movingBlock.getId(), 0, targetParent.getId()));
        }

        List<Integer> statuses = submitTransactionsSimultaneously(document.getId(), requestBodies);

        assertThat(statuses.stream().filter(status -> status == 200).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(status -> status == 409).count()).isEqualTo(9);

        Block reloadedBlock = blockRepository.findByIdAndDeletedAtIsNull(movingBlock.getId()).orElseThrow();
        assertThat(reloadedBlock.getVersion()).isEqualTo(1);
        assertThat(reloadedBlock.getParentId()).isEqualTo(targetParent.getId());
    }

    @Test
    @DisplayName("경쟁형_같은 block delete 요청 10개를 출발선만 맞춰 동시에 보내면 하나만 성공한다")
    void racingDeleteOnSameBlockTenRequestsOnlyOneSucceeds() throws Exception {
        Document document = document("문서");
        Block rootBlock = block(document, null, "삭제 대상", "000000000001000000000000");

        List<String> requestBodies = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            requestBodies.add(deleteRequest("batch-delete-race-" + index, rootBlock.getId(), 0));
        }

        List<Integer> statuses = submitTransactionsSimultaneously(document.getId(), requestBodies);

        assertThat(statuses.stream().filter(status -> status == 200).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(status -> status == 404 || status == 409).count()).isEqualTo(9);

        Block deletedBlock = blockRepository.findById(rootBlock.getId()).orElseThrow();
        assertThat(deletedBlock.getDeletedAt()).isNotNull();
        assertThat(deletedBlock.getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("경쟁형_서로 다른 block move 요청 10개도 같은 documentVersion이면 하나만 성공한다")
    void racingMoveOnDifferentBlocksTenRequestsOnlyOneSucceedsBecauseDocumentVersionConflicts() throws Exception {
        Document document = document("문서");
        Block targetParent = block(document, null, "대상 부모", "000000000001000000000000");

        List<Block> blocks = new ArrayList<>();
        List<String> requestBodies = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            Block block = block(document, null, "블록-" + index, String.format("%024d", index + 2));
            blocks.add(block);
            requestBodies.add(moveRequest("batch-move-race-" + index, block.getId(), 0, targetParent.getId()));
        }

        List<Integer> statuses = submitTransactionsSimultaneously(document.getId(), requestBodies);

        assertThat(statuses.stream().filter(status -> status == 200).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(status -> status == 409).count()).isEqualTo(9);

        long movedCount = blocks.stream()
                .map(block -> blockRepository.findByIdAndDeletedAtIsNull(block.getId()).orElseThrow())
                .filter(block -> targetParent.getId().equals(block.getParentId()))
                .count();
        assertThat(movedCount).isEqualTo(1);
    }

    @Test
    @DisplayName("경쟁형_서로 다른 block delete 요청 10개도 같은 documentVersion이면 하나만 성공한다")
    void racingDeleteOnDifferentBlocksTenRequestsOnlyOneSucceedsBecauseDocumentVersionConflicts() throws Exception {
        Document document = document("문서");

        List<Block> blocks = new ArrayList<>();
        List<String> requestBodies = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            Block block = block(document, null, "삭제 블록-" + index, String.format("%024d", index + 1));
            blocks.add(block);
            requestBodies.add(deleteRequest("batch-delete-race-" + index, block.getId(), 0));
        }

        List<Integer> statuses = submitTransactionsSimultaneously(document.getId(), requestBodies);

        assertThat(statuses.stream().filter(status -> status == 200).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(status -> status == 409).count()).isEqualTo(9);

        long deletedCount = blocks.stream()
                .map(block -> blockRepository.findById(block.getId()).orElseThrow())
                .filter(block -> block.getDeletedAt() != null)
                .count();
        assertThat(deletedCount).isEqualTo(1);
    }

    @Test
    @DisplayName("경쟁형_새 block create 5개와 기존 block replace_content 5개를 같은 documentVersion으로 동시에 보내면 하나만 성공한다")
    void racingCreateAndReplaceMixedRequestsOnlyOneSucceedsBecauseDocumentVersionConflicts() throws Exception {
        Document document = document("문서");
        Block existingBlock = block(document, null, "기존 블록", "000000000001000000000000");

        List<String> requestBodies = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            requestBodies.add(createRequest("batch-create-race-" + index, "tmp:create:" + index));
            requestBodies.add(replaceContentRequest("batch-replace-race-" + index, existingBlock.getId(), 0, "수정-" + index));
        }

        List<Integer> statuses = submitTransactionsSimultaneously(document.getId(), requestBodies);

        assertThat(statuses.stream().filter(status -> status == 200).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(status -> status == 409).count()).isEqualTo(9);

        List<Block> activeBlocks = blockRepository.findActiveByDocumentIdOrderBySortKey(document.getId());
        Block reloadedExistingBlock = blockRepository.findByIdAndDeletedAtIsNull(existingBlock.getId()).orElseThrow();
        boolean createSucceeded = activeBlocks.size() == 2;
        boolean replaceSucceeded = reloadedExistingBlock.getVersion() == 1;
        assertThat(createSucceeded ^ replaceSucceeded).isTrue();
    }

    @Test
    @DisplayName("재시도_같은 block replace_content 경쟁에서 충돌한 뒤 최신 block version과 documentVersion으로 다시 보내면 성공한다")
    void retryAfterConflictWithLatestVersionsSucceeds() throws Exception {
        Document document = document("문서");
        Block block = block(document, null, "기존 블록", "000000000001000000000000");

        List<MvcResult> raceResults = submitTransactionsSimultaneouslyForResults(
                document.getId(),
                List.of(
                        replaceContentRequest("batch-race-a", block.getId(), 0, "첫 번째 수정"),
                        replaceContentRequest("batch-race-b", block.getId(), 0, "두 번째 수정")
                )
        );

        assertThat(raceResults.stream().filter(result -> result.getResponse().getStatus() == 200).count()).isEqualTo(1);
        assertThat(raceResults.stream().filter(result -> result.getResponse().getStatus() == 409).count()).isEqualTo(1);

        Block reloadedBlock = blockRepository.findByIdAndDeletedAtIsNull(block.getId()).orElseThrow();
        Document reloadedDocument = documentRepository.findByIdAndDeletedAtIsNull(document.getId()).orElseThrow();

        MvcResult retryResult = mockMvc.perform(post("/v1/documents/{documentId}/transactions", document.getId())
                        .contentType("application/json")
                        .header("X-User-Id", "user-456")
                        .content(replaceContentRequest(
                                "batch-retry",
                                reloadedDocument.getVersion(),
                                block.getId(),
                                reloadedBlock.getVersion(),
                                "재시도 성공"
                        )))
                .andReturn();

        assertThat(retryResult.getResponse().getStatus()).isEqualTo(200);
        assertThat(extractDocumentVersion(retryResult)).isEqualTo(2);
        assertThat(blockRepository.findByIdAndDeletedAtIsNull(block.getId()).orElseThrow().getContent())
                .contains("\"text\":\"재시도 성공\"");
    }

    @Test
    @DisplayName("경쟁형_같은 block replace_content no-op 요청 10개를 동시에 보내면 모두 성공하고 documentVersion은 유지된다")
    void racingReplaceContentNoOpTenRequestsAllSucceedWithoutIncrementingDocumentVersion() throws Exception {
        Document document = document("문서");
        Block block = block(document, null, "같은 내용", "000000000001000000000000");

        List<String> requestBodies = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            requestBodies.add(replaceContentRequest("batch-noop-race-" + index, block.getId(), 0, "같은 내용"));
        }

        List<MvcResult> results = submitTransactionsSimultaneouslyForResults(document.getId(), requestBodies);

        assertThat(results.stream().filter(result -> result.getResponse().getStatus() == 200).count()).isEqualTo(10);
        List<Integer> documentVersions = new ArrayList<>();
        List<String> operationStatuses = new ArrayList<>();
        for (MvcResult result : results) {
            documentVersions.add(extractDocumentVersion(result));
            operationStatuses.add(extractAppliedOperationStatus(result));
        }
        assertThat(documentVersions).containsOnly(0);
        assertThat(operationStatuses).containsOnly("NO_OP");

        Block reloadedBlock = blockRepository.findByIdAndDeletedAtIsNull(block.getId()).orElseThrow();
        Document reloadedDocument = documentRepository.findByIdAndDeletedAtIsNull(document.getId()).orElseThrow();
        assertThat(reloadedBlock.getVersion()).isEqualTo(0);
        assertThat(reloadedDocument.getVersion()).isEqualTo(0);
    }

    @Test
    @DisplayName("경쟁형_같은 block move no-op 요청 10개를 동시에 보내면 모두 성공하고 documentVersion은 유지된다")
    void racingMoveNoOpTenRequestsAllSucceedWithoutIncrementingDocumentVersion() throws Exception {
        Document document = document("문서");
        Block movingBlock = block(document, null, "이동 대상", "000000000001000000000000");

        List<String> requestBodies = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            requestBodies.add(moveRequest("batch-noop-move-" + index, movingBlock.getId(), 0, null));
        }

        List<MvcResult> results = submitTransactionsSimultaneouslyForResults(document.getId(), requestBodies);

        assertThat(results.stream().filter(result -> result.getResponse().getStatus() == 200).count()).isEqualTo(10);
        List<Integer> documentVersions = new ArrayList<>();
        List<String> operationStatuses = new ArrayList<>();
        for (MvcResult result : results) {
            documentVersions.add(extractDocumentVersion(result));
            operationStatuses.add(extractAppliedOperationStatus(result));
        }
        assertThat(documentVersions).containsOnly(0);
        assertThat(operationStatuses).containsOnly("NO_OP");

        Block reloadedBlock = blockRepository.findByIdAndDeletedAtIsNull(movingBlock.getId()).orElseThrow();
        Document reloadedDocument = documentRepository.findByIdAndDeletedAtIsNull(document.getId()).orElseThrow();
        assertThat(reloadedBlock.getVersion()).isEqualTo(0);
        assertThat(reloadedDocument.getVersion()).isEqualTo(0);
    }

    private void serializeConcurrentUpdates(int participants) throws Exception {
        Answer<Object> answer = serializingAnswer(participants);
        Mockito.doAnswer(answer)
                .when(blockService)
                .update(Mockito.any(UUID.class), Mockito.anyString(), Mockito.anyInt(), Mockito.anyString());
    }

    private void serializeConcurrentCreates(int participants) throws Exception {
        Answer<Object> answer = serializingAnswer(participants);
        Mockito.doAnswer(answer)
                .when(blockService)
                .create(Mockito.any(Document.class), Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.anyString());
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

    private void serializeConcurrentCreateAndUpdate(int participants) throws Exception {
        Answer<Object> answer = serializingAnswer(participants);
        Mockito.doAnswer(answer)
                .when(blockService)
                .create(Mockito.any(Document.class), Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.anyString());
        Mockito.doAnswer(answer)
                .when(blockService)
                .update(Mockito.any(UUID.class), Mockito.anyString(), Mockito.anyInt(), Mockito.anyString());
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

    private List<Integer> submitTransactionsSimultaneously(UUID documentId, List<String> requestBodies) throws Exception {
        return submitTransactionsSimultaneouslyForResults(documentId, requestBodies).stream()
                .map(result -> result.getResponse().getStatus())
                .toList();
    }

    private List<MvcResult> submitTransactionsSimultaneouslyForResults(UUID documentId, List<String> requestBodies) throws Exception {
        CountDownLatch ready = new CountDownLatch(requestBodies.size());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requestBodies.size());

        try {
            List<Callable<MvcResult>> tasks = requestBodies.stream()
                    .<Callable<MvcResult>>map(requestBody -> () -> {
                        ready.countDown();
                        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                        return mockMvc.perform(post("/v1/documents/{documentId}/transactions", documentId)
                                        .contentType("application/json")
                                        .header("X-User-Id", "user-456")
                                        .content(requestBody))
                                .andReturn();
                    })
                    .toList();

            List<Future<MvcResult>> futures = tasks.stream()
                    .map(executor::submit)
                    .toList();

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdown();
        }
    }

    private int extractDocumentVersion(MvcResult result) throws Exception {
        return responseBody(result).path("data").path("documentVersion").asInt();
    }

    private int extractErrorCode(MvcResult result) throws Exception {
        return responseBody(result).path("code").asInt();
    }

    private String extractAppliedOperationStatus(MvcResult result) throws Exception {
        return responseBody(result).path("data").path("appliedOperations").get(0).path("status").asText();
    }

    private JsonNode responseBody(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String replaceContentRequest(String batchId, UUID blockId, int version, String text) {
        return replaceContentRequest(batchId, 0, blockId, version, text);
    }

    private String replaceContentRequest(String batchId, int documentVersion, UUID blockId, int version, String text) {
        return """
                {
                  "clientId": "web-editor",
                  "documentVersion": %d,
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
                """.formatted(documentVersion, batchId, blockId, version, text);
    }

    private String moveRequest(String batchId, UUID blockId, int version, UUID parentId) {
        String parentReference = parentId == null ? "" : """
                      "parentRef": "%s"
                    """.formatted(parentId);
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
                      "version": %d%s
                    }
                  ]
                }
                """.formatted(batchId, blockId, version, parentReference.isBlank() ? "" : ",\n" + parentReference);
    }

    private String createRequest(String batchId, String tempBlockRef) {
        return """
                {
                  "clientId": "web-editor",
                  "documentVersion": 0,
                  "batchId": "%s",
                  "operations": [
                    {
                      "opId": "op-1",
                      "type": "BLOCK_CREATE",
                      "blockRef": "%s"
                    }
                  ]
                }
                """.formatted(batchId, tempBlockRef);
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
