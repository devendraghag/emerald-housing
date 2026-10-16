package com.emerald.member.exception;

public class FlatNotEligibleException extends RuntimeException {
    public FlatNotEligibleException() { super("The requested flat is not eligible for assignment"); }
}
