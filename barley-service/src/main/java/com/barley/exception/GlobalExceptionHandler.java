package com.barley.exception;

import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Centralised exception handler — catches exceptions thrown anywhere in the controller layer
 * and maps them to consistent {@link ErrorResponse} JSON bodies.
 *
 * <p><b>Why @RestControllerAdvice?</b>
 * Without this, Spring returns an inconsistent HTML error page or a default Spring whiteboard.
 * Centralising error handling here means:
 * <ul>
 *   <li>Controllers stay clean — no try/catch blocks.</li>
 *   <li>All clients receive the same error format.</li>
 *   <li>New exception types need only one new @ExceptionHandler method.</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── Domain exceptions ──────────────────────────────────────────────────

    @ExceptionHandler(BarleyNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            BarleyNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), request, null);
    }

    // ── Validation exceptions ──────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(e -> ErrorResponse.FieldError.builder()
                        .field(e.getField())
                        .rejectedValue(e.getRejectedValue())
                        .message(e.getDefaultMessage())
                        .build())
                .toList();

        log.warn("Validation failed: {} field error(s)", fieldErrors.size());
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Request validation failed", request, fieldErrors);
    }

    // ── Security exceptions ────────────────────────────────────────────────

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuth(
            AuthenticationException ex, HttpServletRequest request) {
        log.warn("Authentication failure: {}", ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage(), request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied: {}", ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action",
                request, null);
    }

    // ── Concurrency exceptions ─────────────────────────────────────────────

    /**
     * Thrown when two concurrent requests try to update the same record.
     * The caller should refresh the resource and retry.
     */
    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(
            OptimisticLockException ex, HttpServletRequest request) {
        log.warn("Optimistic lock conflict on request to {}", request.getRequestURI());
        return build(HttpStatus.CONFLICT, "CONFLICT",
                "The resource was modified by another request. Please refresh and retry.",
                request, null);
    }

    // ── Catch-all ──────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAll(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}: {}", request.getMethod(),
                request.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred. Please contact support.", request, null);
    }

    // ── Helper ─────────────────────────────────────────────────────────────

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status, String error, String message,
            HttpServletRequest request, List<ErrorResponse.FieldError> fieldErrors) {

        ErrorResponse body = ErrorResponse.builder()
                .status(status.value())
                .error(error)
                .message(message)
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.status(status).body(body);
    }
}
