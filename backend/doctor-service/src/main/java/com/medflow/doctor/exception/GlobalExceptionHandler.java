package com.medflow.doctor.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    record ErrorBody(Instant timestamp, int status, String message, Map<String, String> fields) {}

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorBody> api(ApiException e) { return body(e.status(), e.getMessage(), Map.of()); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorBody> validation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return body(HttpStatus.BAD_REQUEST, "Validation failed", fields);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorBody> malformed() { return body(HttpStatus.BAD_REQUEST, "Invalid request", Map.of()); }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorBody> conflict() { return body(HttpStatus.CONFLICT, "Doctor profile conflicts with an existing record", Map.of()); }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorBody> unexpected() { return body(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", Map.of()); }

    private ResponseEntity<ErrorBody> body(HttpStatus status, String message, Map<String, String> fields) {
        return ResponseEntity.status(status).body(new ErrorBody(Instant.now(), status.value(), message, fields));
    }
}
