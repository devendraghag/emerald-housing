package com.emerald.member.exception;

public class SocietyDependencyException extends RuntimeException {
    private final int status;
    private final String code;
    public SocietyDependencyException(int status, String code, String message, Throwable cause) {
        super(message, cause); this.status=status; this.code=code;
    }
    public int getStatus() { return status; }
    public String getCode() { return code; }
}
