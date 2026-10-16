package com.emerald.society.dto;

import java.util.UUID;

public record FlatResponse(UUID flatId, String flatNumber, UUID floorId, int floorNumber, String status) { }
