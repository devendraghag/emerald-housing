package com.emerald.member.exception;

import java.util.UUID;

public class FlatNotFoundException extends RuntimeException {
    public FlatNotFoundException(UUID flatId) { super("Flat " + flatId + " was not found in Society Service"); }
}
