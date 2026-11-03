package com.emerald.member.dto;

import java.util.UUID;

/** Minimal internal flat-directory response used only for notice audience choices. */
public record SocietyNoticeFlatOption(UUID flatId, String flatNumber, String status) { }
