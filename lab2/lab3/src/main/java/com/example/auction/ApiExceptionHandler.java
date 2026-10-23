package com.example.auction;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
@RestControllerAdvice
public class ApiExceptionHandler {
 @ExceptionHandler(ResponseStatusException.class)
 ResponseEntity<?> status(ResponseStatusException e) {
  return ResponseEntity.status(e.getStatusCode()).body(Map.of("status",e.getStatusCode().value(),"message",String.valueOf(e.getReason())));
 }
 @ExceptionHandler(DataIntegrityViolationException.class)
 ResponseEntity<?> integrity(DataIntegrityViolationException e) {
  return ResponseEntity.status(409).body(Map.of("status",409,"message","Database constraint: duplicate value, invalid reference, or referenced record cannot be deleted"));
 }
 @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
 ResponseEntity<?> invalid(Exception e) { return ResponseEntity.badRequest().body(Map.of("status",400,"message","Invalid JSON or parameter type")); }
}
