package com.medflow.billing;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.server.ResponseStatusException;
@RestControllerAdvice public class Errors {
  @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> status(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("status",e.getStatusCode().value(),"message",e.getReason()==null?"Request rejected":e.getReason()));}
  @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class}) ResponseEntity<?> bad(Exception e){return ResponseEntity.badRequest().body(Map.of("status",400,"message","Request fields are invalid or not allowed"));}
  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class) ResponseEntity<?> conflict(Exception e){return ResponseEntity.status(409).body(Map.of("status",409,"message","Record conflicts with existing data or constraints"));}
  @ExceptionHandler(Exception.class) ResponseEntity<?> failed(Exception e){return ResponseEntity.internalServerError().body(Map.of("status",500,"message","Request failed; no sensitive diagnostic details are exposed"));}
}
