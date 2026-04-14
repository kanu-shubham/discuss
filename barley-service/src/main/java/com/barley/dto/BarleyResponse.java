package com.barley.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Outbound DTO returned to API consumers.
 *
 * <p>Using Java {@code record} (immutable, auto-generated equals/hashCode/toString)
 * is idiomatic for DTOs in Java 17+.
 */
@Builder
@Schema(description = "Barley inventory item response")
public record BarleyResponse(

        @Schema(description = "Unique identifier", example = "1")
        Long id,

        @Schema(description = "Product name", example = "Premium Malt Barley")
        String name,

        @Schema(description = "Barley variety", example = "Two-Row")
        String variety,

        @Schema(description = "Origin country/region", example = "Scotland")
        String origin,

        @Schema(description = "Quantity in kilograms", example = "5000.000")
        BigDecimal quantityKg,

        @Schema(description = "Price per kilogram", example = "1.25")
        BigDecimal pricePerKg,

        @Schema(description = "Harvest year", example = "2023")
        Integer harvestYear,

        @Schema(description = "Stock availability", example = "true")
        Boolean inStock,

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        @Schema(description = "Record creation timestamp")
        LocalDateTime createdAt,

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        @Schema(description = "Record last-updated timestamp")
        LocalDateTime updatedAt
) {}
