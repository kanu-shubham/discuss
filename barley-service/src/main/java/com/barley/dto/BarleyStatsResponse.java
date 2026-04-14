package com.barley.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
@Schema(description = "Aggregated inventory statistics")
public record BarleyStatsResponse(

        @Schema(description = "Total items in inventory")
        long totalItems,

        @Schema(description = "Number of items out of stock")
        long outOfStockCount,

        @Schema(description = "Total kilograms of in-stock barley")
        BigDecimal totalStockKg,

        @Schema(description = "Average price per kilogram across all items")
        BigDecimal averagePricePerKg
) {}
