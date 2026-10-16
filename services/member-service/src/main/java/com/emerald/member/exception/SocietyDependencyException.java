package com.emerald.member.exception;

public class SocietyDependencyException extends RuntimeException {
    private final int status;
    private final String code;
    private final boolean retryable;
    public SocietyDependencyException(int status, String code, String message, Throwable cause) {
        this(status, code, message, cause, false);
    }
    public SocietyDependencyException(int status, String code, String message, Throwable cause, boolean retryable) {
        super(message, cause); this.status=status; this.code=code; this.retryable=retryable;
    }
    public int getStatus() { return status; }
    public String getCode() { return code; }
    public boolean isRetryable() { return retryable; }
}
