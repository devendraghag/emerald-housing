package com.emerald.member.dto;

import java.util.UUID;

public record SocietyFlatResponse(UUID flatId, String flatNumber, UUID floorId, int floorNumber, String status) { }
