package com.barley.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * Inbound DTO for creating/updating a barley inventory record.
 *
 * <p>DTOs decouple the API contract from the DB schema — if the entity changes
 * (column rename, new audit field) the API stays stable.
 *
 * <p>Bean Validation annotations are enforced by {@code @Valid} in the controller.
 * Validation failures are caught by {@link com.barley.exception.GlobalExceptionHandler}.
 */
@Builder
@Schema(description = "Request payload for barley inventory operations")
public record BarleyRequest(

        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        @Schema(description = "Descriptive name of the barley product", example = "Premium Malt Barley")
        String name,

        @NotBlank(message = "Variety is required")
        @Schema(description = "Barley variety (e.g. Two-Row, Six-Row, Winter)", example = "Two-Row")
        String variety,

        @NotBlank(message = "Origin country/region is required")
        @Schema(description = "Country or region of origin", example = "Scotland")
        String origin,

        @NotNull(message = "Quantity is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "Quantity must be positive")
        @Digits(integer = 9, fraction = 3, message = "Quantity format: up to 9 integer digits, 3 decimal places")
        @Schema(description = "Quantity in kilograms", example = "5000.000")
        BigDecimal quantityKg,

        @NotNull(message = "Price per kg is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "Price must be positive")
        @Digits(integer = 8, fraction = 2, message = "Price format: up to 8 integer digits, 2 decimal places")
        @Schema(description = "Price per kilogram in GBP", example = "1.25")
        BigDecimal pricePerKg,

        @NotNull(message = "Harvest year is required")
        @Min(value = 1900, message = "Harvest year must be after 1900")
        @Max(value = 2100, message = "Harvest year must be before 2100")
        @Schema(description = "Year the barley was harvested", example = "2023")
        Integer harvestYear,

        @Schema(description = "Whether the product is currently in stock", example = "true")
        Boolean inStock
) {}
