package com.emerald.member.exception;

public class DuplicateMembershipException extends RuntimeException {
    public DuplicateMembershipException() { super("An active membership already exists for this member, flat and relationship"); }
}
