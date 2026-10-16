package com.emerald.member.exception;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException() {
        super("This Idempotency-Key was already used for a different request or is still processing");
    }
}
