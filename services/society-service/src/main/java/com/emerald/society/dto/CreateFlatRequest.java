package com.emerald.society.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateFlatRequest(@Min(1) @Max(18) int floorNumber,
                                @NotBlank @Size(max = 40) String flatNumber) { }
