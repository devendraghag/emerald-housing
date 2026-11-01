package com.emerald.society.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record FloorRequest(@Min(1) @Max(99) int floorNumber) { }
