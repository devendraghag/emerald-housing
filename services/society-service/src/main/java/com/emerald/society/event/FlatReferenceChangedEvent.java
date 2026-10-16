package com.emerald.society.event;

import java.util.UUID;

public record FlatReferenceChangedEvent(UUID flatId, Integer previousFloorNumber, Integer currentFloorNumber) { }
