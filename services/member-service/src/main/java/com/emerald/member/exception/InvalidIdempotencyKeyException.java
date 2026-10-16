package com.emerald.member.exception;

public class InvalidIdempotencyKeyException extends RuntimeException {
    public InvalidIdempotencyKeyException() {
        super("Idempotency-Key must contain 1 to 128 safe characters");
    }
}
