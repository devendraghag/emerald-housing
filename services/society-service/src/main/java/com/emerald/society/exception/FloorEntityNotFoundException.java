package com.emerald.society.exception;

public class FloorEntityNotFoundException extends RuntimeException {
    public FloorEntityNotFoundException(int number) { super("Floor not found: " + number); }
}
