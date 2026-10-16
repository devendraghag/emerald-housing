package com.emerald.member.exception;

public class InvalidMembershipEndDateException extends RuntimeException {
    public InvalidMembershipEndDateException() { super("endDate cannot be before startDate"); }
}
