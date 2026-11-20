package com.example.auction;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.Map;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> business(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("status",e.getStatusCode().value(),"error",e.getReason()==null?"Request rejected":e.getReason()));}
    @ExceptionHandler(AccessDeniedException.class) ResponseEntity<?> forbidden(){return ResponseEntity.status(403).body(Map.of("status",403,"error","FORBIDDEN"));}
    @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class,IllegalArgumentException.class}) ResponseEntity<?> invalid(){return ResponseEntity.badRequest().body(Map.of("status",400,"error","Invalid request"));}
    @ExceptionHandler(Exception.class) ResponseEntity<?> unexpected(){return ResponseEntity.internalServerError().body(Map.of("status",500,"error","Internal error"));}
}
