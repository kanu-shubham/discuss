package com.barley.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Standardised error envelope returned for all error responses.
 *
 * <p>Consistent error bodies help API consumers parse errors programmatically
 * without inspecting HTTP status codes alone.
 */
@Builder
@Schema(description = "Standard error response envelope")
public record ErrorResponse(

        @Schema(description = "HTTP status code", example = "404")
        int status,

        @Schema(description = "Short error type", example = "NOT_FOUND")
        String error,

        @Schema(description = "Human-readable message", example = "Barley item not found with id: 42")
        String message,

        @Schema(description = "Request path that caused the error", example = "/api/barley/42")
        String path,

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        @Schema(description = "Timestamp of the error")
        LocalDateTime timestamp,

        @Schema(description = "Field-level validation errors (present on 400 responses)")
        List<FieldError> fieldErrors
) {

    @Builder
    public record FieldError(
            @Schema(description = "Field name", example = "quantityKg")
            String field,
            @Schema(description = "Rejected value", example = "-5")
            Object rejectedValue,
            @Schema(description = "Validation message", example = "Quantity must be positive")
            String message
    ) {}
}
