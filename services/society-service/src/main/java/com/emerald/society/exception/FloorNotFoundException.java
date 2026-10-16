package com.emerald.society.exception;

public class FloorNotFoundException extends RuntimeException {

    public FloorNotFoundException(int floorNumber) {
        super("Floor " + floorNumber + " does not exist");
    }
}
