package com.emerald.member.exception;

public class DuplicateMobileNumberException extends RuntimeException {

    public DuplicateMobileNumberException() {
        super("An active member already uses this mobile number");
    }
}
