package com.emerald.member.exception;

public class InactiveMemberException extends RuntimeException {
    public InactiveMemberException() { super("Inactive members cannot be assigned to flats"); }
}
