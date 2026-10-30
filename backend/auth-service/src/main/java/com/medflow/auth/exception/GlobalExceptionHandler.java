package com.medflow.auth.exception;
import jakarta.servlet.http.HttpServletRequest; import java.time.Instant; import java.util.*; import org.springframework.http.*; import org.springframework.http.converter.HttpMessageNotReadableException; import org.springframework.web.bind.MethodArgumentNotValidException; import org.springframework.web.bind.annotation.*;
@RestControllerAdvice public class GlobalExceptionHandler {
 @ExceptionHandler(ApiException.class) public ResponseEntity<ApiError> api(ApiException e,HttpServletRequest r){return response(e.getStatus(),e.getMessage(),r,Map.of());}
 @ExceptionHandler(MethodArgumentNotValidException.class) public ResponseEntity<ApiError> validation(MethodArgumentNotValidException e,HttpServletRequest r){Map<String,String> fields=new LinkedHashMap<>();e.getBindingResult().getFieldErrors().forEach(x->fields.putIfAbsent(x.getField(),x.getDefaultMessage()));return response(HttpStatus.BAD_REQUEST,"Validation failed",r,fields);}
 @ExceptionHandler(HttpMessageNotReadableException.class) public ResponseEntity<ApiError> malformed(HttpServletRequest r){return response(HttpStatus.BAD_REQUEST,"Malformed request body",r,Map.of());}
 @ExceptionHandler(Exception.class) public ResponseEntity<ApiError> unknown(HttpServletRequest r){return response(HttpStatus.INTERNAL_SERVER_ERROR,"An unexpected error occurred",r,Map.of());}
 private ResponseEntity<ApiError> response(HttpStatus s,String m,HttpServletRequest r,Map<String,String> f){return ResponseEntity.status(s).body(new ApiError(Instant.now(),s.value(),s.getReasonPhrase(),m,r.getRequestURI(),f.isEmpty()?null:f));}
}
