package com.nuuly.inventory.config;

import com.nuuly.inventory.domain.InsufficientInventoryException;
import com.nuuly.inventory.domain.SkuNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(SkuNotFoundException.class)
    public ResponseEntity<String> notFound(SkuNotFoundException e) {
        log.warn("404 NOT_FOUND: {}", e.getMessage());
        return text(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(InsufficientInventoryException.class)
    public ResponseEntity<String> insufficient(InsufficientInventoryException e) {
        log.warn("400 INSUFFICIENT_INVENTORY");
        return text(HttpStatus.BAD_REQUEST, "Insufficient inventory");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> invalid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("Invalid request");
        log.warn("400 VALIDATION_ERROR: {}", msg);
        return text(HttpStatus.BAD_REQUEST, msg);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<String> malformed(HttpMessageNotReadableException e) {
        log.warn("400 MALFORMED_REQUEST: {}", e.getMessage());
        return text(HttpStatus.BAD_REQUEST, "Malformed request body");
    }

    private ResponseEntity<String> text(HttpStatus status, String body) {
        return ResponseEntity.status(status)
                .contentType(MediaType.TEXT_PLAIN)
                .body(body);
    }
}
