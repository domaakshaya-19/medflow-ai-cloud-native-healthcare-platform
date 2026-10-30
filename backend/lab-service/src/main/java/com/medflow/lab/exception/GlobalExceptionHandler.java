package com.medflow.lab.exception;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiException.class) ResponseEntity<ApiError> api(ApiException e){return body(e.status(),e.getMessage(),Map.of());}
    @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<ApiError> validation(MethodArgumentNotValidException e){Map<String,String> fields=new LinkedHashMap<>();e.getBindingResult().getFieldErrors().forEach(f->fields.putIfAbsent(f.getField(),f.getDefaultMessage()));return body(HttpStatus.BAD_REQUEST,"Validation failed",fields);}
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class}) ResponseEntity<ApiError> malformed(){return body(HttpStatus.BAD_REQUEST,"Invalid request",Map.of());}
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<ApiError> conflict(){return body(HttpStatus.CONFLICT,"Lab record conflicts with existing data",Map.of());}
    @ExceptionHandler(Exception.class) ResponseEntity<ApiError> unexpected(){return body(HttpStatus.INTERNAL_SERVER_ERROR,"Unexpected server error",Map.of());}
    private ResponseEntity<ApiError> body(HttpStatus status,String message,Map<String,String> fields){return ResponseEntity.status(status).body(new ApiError(Instant.now(),status.value(),message,fields));}
}
