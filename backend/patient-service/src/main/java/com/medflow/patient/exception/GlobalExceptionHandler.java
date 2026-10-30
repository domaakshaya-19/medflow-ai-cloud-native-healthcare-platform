package com.medflow.patient.exception;

import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import java.time.Instant;
import java.util.*;

@RestControllerAdvice
public class GlobalExceptionHandler {
 record ErrorBody(Instant timestamp,int status,String message,Map<String,String> fields){}
 @ExceptionHandler(ApiException.class) ResponseEntity<ErrorBody> api(ApiException e){return body(e.status,e.getMessage(),Map.of());}
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<ErrorBody> validation(MethodArgumentNotValidException e){Map<String,String> m=new LinkedHashMap<>();e.getBindingResult().getFieldErrors().forEach(f->m.putIfAbsent(f.getField(),f.getDefaultMessage()));return body(HttpStatus.BAD_REQUEST,"Validation failed",m);}
 @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class}) ResponseEntity<ErrorBody> malformed(){return body(HttpStatus.BAD_REQUEST,"Invalid request",Map.of());}
 @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<ErrorBody> conflict(){return body(HttpStatus.CONFLICT,"Profile conflicts with an existing record",Map.of());}
 @ExceptionHandler(Exception.class) ResponseEntity<ErrorBody> unexpected(){return body(HttpStatus.INTERNAL_SERVER_ERROR,"Unexpected server error",Map.of());}
 private ResponseEntity<ErrorBody> body(HttpStatus s,String m,Map<String,String> f){return ResponseEntity.status(s).body(new ErrorBody(Instant.now(),s.value(),m,f));}
}
