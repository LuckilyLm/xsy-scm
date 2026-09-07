package com.xianshuyuan.scm.order.service;

public final class IdempotencyGuard {
    private IdempotencyGuard() {
    }

    public static boolean matches(String storedHash, String requestHash) {
        return java.util.Objects.equals(storedHash, requestHash);
    }

    public static void requireMatching(String storedHash, String requestHash) {
        if (!matches(storedHash, requestHash)) throw new IdempotencyConflictException();
    }
}
