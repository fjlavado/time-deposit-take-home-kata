package org.ikigaidigital.infrastructure.configuration;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class BalanceUpdateConflictHandler {
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Void> concurrentBalanceUpdate() {
        // The losing transaction has rolled back. A new request reloads current versions.
        return ResponseEntity.status(409).build();
    }
}
