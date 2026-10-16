package com.emerald.member.exception;

import java.util.UUID;

public class MemberNotFoundException extends RuntimeException {

    public MemberNotFoundException(UUID memberId) {
        super("Member " + memberId + " was not found");
    }
}
