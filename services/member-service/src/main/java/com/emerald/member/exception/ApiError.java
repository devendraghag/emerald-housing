package com.emerald.member.exception;

import java.time.Instant;
import java.util.Map;

public record ApiError(Instant timestamp, int status, String error, String code,
                       String message, String path, Map<String, String> details) {
}
