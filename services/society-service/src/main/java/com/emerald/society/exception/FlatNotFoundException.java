package com.emerald.society.exception;

import java.util.UUID;

public class FlatNotFoundException extends RuntimeException {
    public FlatNotFoundException(UUID id) { super("Flat not found: " + id); }
}
