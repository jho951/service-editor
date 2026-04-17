package com.documents.support;

import java.math.BigInteger;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
public class OrderedSortKeyGenerator {

    private static final int KEY_WIDTH = 24;
    private static final int KEY_RADIX = 36;
    private static final BigInteger MIN_VALUE = BigInteger.ZERO;
    private static final BigInteger MAX_VALUE = BigInteger.valueOf(KEY_RADIX).pow(KEY_WIDTH).subtract(BigInteger.ONE);
    private static final BigInteger SPACE_UPPER_BOUND = MAX_VALUE.add(BigInteger.ONE);
    // 초반 삽입에서 gap 고갈 완화용 기본 간격
    private static final BigInteger DEFAULT_STEP = BigInteger.valueOf(KEY_RADIX).pow(KEY_WIDTH / 2);

    public <T> String generate(
            List<T> siblings,
            Function<T, UUID> idExtractor,
            Function<T, String> sortKeyExtractor,
            UUID afterId,
            UUID beforeId
    ) {
        AnchorContext<T> context = resolveContext(siblings, idExtractor, afterId, beforeId);

        if (context.after() == null && context.before() == null) {
            return siblings.isEmpty()
                    ? format(DEFAULT_STEP)
                    : appendAfter(parse(sortKeyExtractor.apply(siblings.get(siblings.size() - 1))));
        }

        if (context.after() != null && context.before() != null) {
            return between(parse(sortKeyExtractor.apply(context.after())), parse(sortKeyExtractor.apply(context.before())));
        }

        if (context.after() != null) {
            return context.nextSibling() == null
                    ? appendAfter(parse(sortKeyExtractor.apply(context.after())))
                    : between(
                            parse(sortKeyExtractor.apply(context.after())),
                            parse(sortKeyExtractor.apply(context.nextSibling()))
                    );
        }

        return context.previousSibling() == null
                ? prependBefore(parse(sortKeyExtractor.apply(context.before())))
                : between(
                        parse(sortKeyExtractor.apply(context.previousSibling())),
                        parse(sortKeyExtractor.apply(context.before()))
                );
    }

    private <T> AnchorContext<T> resolveContext(
            List<T> siblings,
            Function<T, UUID> idExtractor,
            UUID afterId,
            UUID beforeId
    ) {
        if (afterId != null && beforeId != null && afterId.equals(beforeId)) {
            throw new IllegalArgumentException("afterId와 beforeId는 같은 값을 가리킬 수 없습니다.");
        }

        int afterIndex = indexOf(siblings, idExtractor, afterId);
        int beforeIndex = indexOf(siblings, idExtractor, beforeId);

        if (afterId != null && afterIndex < 0) {
            throw new IllegalArgumentException("afterId는 활성 sibling을 가리켜야 합니다.");
        }

        if (beforeId != null && beforeIndex < 0) {
            throw new IllegalArgumentException("beforeId는 활성 sibling을 가리켜야 합니다.");
        }

        if (afterId != null && beforeId != null && afterIndex + 1 != beforeIndex) {
            throw new IllegalArgumentException("afterId와 beforeId는 같은 삽입 간격을 가리켜야 합니다.");
        }

        T after = afterIndex < 0 ? null : siblings.get(afterIndex);
        T before = beforeIndex < 0 ? null : siblings.get(beforeIndex);
        T previousSibling = beforeIndex <= 0 ? null : siblings.get(beforeIndex - 1);
        T nextSibling = afterIndex < 0 || afterIndex + 1 >= siblings.size() ? null : siblings.get(afterIndex + 1);

        return new AnchorContext<>(after, before, previousSibling, nextSibling);
    }

    private <T> int indexOf(List<T> siblings, Function<T, UUID> idExtractor, UUID targetId) {
        if (targetId == null) {
            return -1;
        }

        for (int i = 0; i < siblings.size(); i++) {
            if (targetId.equals(idExtractor.apply(siblings.get(i)))) {
                return i;
            }
        }
        return -1;
    }

    private String appendAfter(BigInteger current) {
        BigInteger candidate = current.add(DEFAULT_STEP);
        if (candidate.compareTo(MAX_VALUE) <= 0) {
            return format(candidate);
        }
        return between(current, null);
    }

    private String prependBefore(BigInteger current) {
        BigInteger candidate = current.subtract(DEFAULT_STEP);
        if (candidate.compareTo(MIN_VALUE) >= 0) {
            return format(candidate);
        }
        return between(null, current);
    }

    private String between(BigInteger lowerExclusive, BigInteger upperExclusive) {
        BigInteger lower = lowerExclusive == null ? MIN_VALUE.subtract(BigInteger.ONE) : lowerExclusive;
        BigInteger upper = upperExclusive == null ? SPACE_UPPER_BOUND : upperExclusive;

        if (lower.compareTo(upper) >= 0) {
            throw new IllegalArgumentException("sibling sortKey 순서가 올바르지 않습니다.");
        }

        BigInteger candidate = lower.add(upper).divide(BigInteger.TWO);
        if (candidate.compareTo(lower) <= 0 || candidate.compareTo(upper) >= 0) {
            throw new SortKeyRebalanceRequiredException();
        }
        return format(candidate);
    }

    private BigInteger parse(String sortKey) {
        try {
            return new BigInteger(sortKey, KEY_RADIX);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("sortKey는 base36 문자열이어야 합니다.", ex);
        }
    }

    private String format(BigInteger value) {
        if (value.compareTo(MIN_VALUE) < 0 || value.compareTo(MAX_VALUE) > 0) {
            throw new SortKeyRebalanceRequiredException();
        }

        String encoded = value.toString(KEY_RADIX).toUpperCase(Locale.ROOT);
        if (encoded.length() > KEY_WIDTH) {
            throw new SortKeyRebalanceRequiredException();
        }

        return "0".repeat(KEY_WIDTH - encoded.length()) + encoded;
    }

    private record AnchorContext<T>(
            T after,
            T before,
            T previousSibling,
            T nextSibling
    ) {
    }

    public static final class SortKeyRebalanceRequiredException extends RuntimeException {
    }
}
