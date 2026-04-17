package com.documents.service;

import java.util.function.Supplier;

final class DocumentVersionIncrementContext {

    private static final ThreadLocal<Boolean> SHOULD_INCREMENT = ThreadLocal.withInitial(() -> true);

    private DocumentVersionIncrementContext() {
    }

    static boolean shouldIncrement() {
        return SHOULD_INCREMENT.get();
    }

    static <T> T runWithoutIncrement(Supplier<T> supplier) {
        boolean previousValue = SHOULD_INCREMENT.get();
        SHOULD_INCREMENT.set(false);
        try {
            return supplier.get();
        } finally {
            SHOULD_INCREMENT.set(previousValue);
        }
    }
}
