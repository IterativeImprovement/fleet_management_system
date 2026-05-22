package com.siyu.fleet_mgmt_sys.exception;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(404, ex.getMessage()));
    }

     @ExceptionHandler(IllegalArgumentException.class)
     public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
         return ResponseEntity.badRequest()
                 .body(new ErrorResponse(400, ex.getMessage()));
     }
}
