package com.emerald.member.exception;

import java.util.UUID;

public class MembershipNotFoundException extends RuntimeException {
    public MembershipNotFoundException(UUID id) { super("Membership not found: " + id); }
}
