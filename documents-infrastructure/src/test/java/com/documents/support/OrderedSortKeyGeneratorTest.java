package com.documents.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("공용 ordered sortKey 생성 유틸 검증")
class OrderedSortKeyGeneratorTest {

    private final OrderedSortKeyGenerator generator = new OrderedSortKeyGenerator();

    @Test
    @DisplayName("성공_형제가 없으면 첫 gap sortKey를 발급한다")
    void generateStartsFromGapSeedWhenSiblingsAreEmpty() {
        assertThat(generator.generate(List.of(), TestNode::id, TestNode::sortKey, null, null))
                .isEqualTo("000000000001000000000000");
    }

    @Test
    @DisplayName("성공_afterId와 다음 형제 사이 위치의 gap sortKey를 발급한다")
    void generateMidpointBetweenAdjacentSiblings() {
        TestNode first = new TestNode(UUID.randomUUID(), "000000000001000000000000");
        TestNode second = new TestNode(UUID.randomUUID(), "000000000002000000000000");

        assertThat(generator.generate(List.of(first, second), TestNode::id, TestNode::sortKey, first.id(), null))
                .isEqualTo("000000000001I00000000000");
    }

    @Test
    @DisplayName("성공_beforeId만 있으면 바로 위 위치의 gap sortKey를 발급한다")
    void generateBeforeFirstSibling() {
        TestNode first = new TestNode(UUID.randomUUID(), "000000000001000000000000");

        assertThat(generator.generate(List.of(first), TestNode::id, TestNode::sortKey, null, first.id()))
                .isEqualTo("000000000000000000000000");
    }

    @Test
    @DisplayName("실패_afterId와 beforeId가 같은 간격을 가리키지 않으면 예외를 던진다")
    void generateRejectsMismatchedGap() {
        TestNode first = new TestNode(UUID.randomUUID(), "000000000001000000000000");
        TestNode second = new TestNode(UUID.randomUUID(), "000000000002000000000000");
        TestNode third = new TestNode(UUID.randomUUID(), "000000000003000000000000");

        assertThatThrownBy(() -> generator.generate(
                List.of(first, second, third),
                TestNode::id,
                TestNode::sortKey,
                first.id(),
                third.id()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("afterId와 beforeId는 같은 삽입 간격을 가리켜야 합니다.");
    }

    @Test
    @DisplayName("실패_gap이 더 없으면 재정렬 필요 예외를 던진다")
    void generateThrowsWhenGapIsExhausted() {
        TestNode first = new TestNode(UUID.randomUUID(), "000000000000000000000000");
        TestNode second = new TestNode(UUID.randomUUID(), "000000000000000000000001");

        assertThatThrownBy(() -> generator.generate(
                List.of(first, second),
                TestNode::id,
                TestNode::sortKey,
                first.id(),
                second.id()
        ))
                .isInstanceOf(OrderedSortKeyGenerator.SortKeyRebalanceRequiredException.class);
    }

    private record TestNode(UUID id, String sortKey) {
    }
}
