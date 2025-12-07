package com.rag.lecturelens.exceptionHandler;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "timestamp", OffsetDateTime.now().toString(),
                        "error", "BAD_REQUEST",
                        "message", e.getMessage()
                ));
    }

    @ExceptionHandler(UsageLimitExceededException.class)
    public ResponseEntity<String> handleUsageLimit(UsageLimitExceededException e) {
        return ResponseEntity.status(429).body(e.getMessage());
    }
}
