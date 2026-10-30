package com.medflow.prescription.exception;

import java.time.Instant;
import java.util.Map;

public record ApiError(Instant timestamp, int status, String message, Map<String, String> fields) {
}
