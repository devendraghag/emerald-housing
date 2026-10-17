package com.emerald.member.exception;

public class MembershipLockUnavailableException extends RuntimeException {
    public MembershipLockUnavailableException(String message) {
        super(message);
    }

    public MembershipLockUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
