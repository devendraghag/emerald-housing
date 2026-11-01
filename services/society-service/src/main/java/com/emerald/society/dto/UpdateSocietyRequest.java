package com.emerald.society.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateSocietyRequest(@NotBlank @Size(max = 200) String name) { }
