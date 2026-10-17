package com.emerald.member.exception;

public class MembershipUpdateInProgressException extends RuntimeException {
    public MembershipUpdateInProgressException() {
        super("Another membership update for this member is still in progress. Please retry.");
    }
}
