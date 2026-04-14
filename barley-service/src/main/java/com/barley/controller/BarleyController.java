package com.barley.controller;

import com.barley.dto.*;
import com.barley.exception.ErrorResponse;
import com.barley.service.BarleyService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for the Barley Inventory resource.
 *
 * <p><b>Production REST conventions followed:</b>
 * <ul>
 *   <li>Resource noun in URL ({@code /barley}), HTTP verbs express action.</li>
 *   <li>201 Created with Location header on POST.</li>
 *   <li>204 No Content on DELETE.</li>
 *   <li>Pagination via Spring's {@link Pageable} with sensible defaults.</li>
 *   <li>Filtering as query params — never in the path.</li>
 *   <li>Rate limiting per endpoint via Resilience4j.</li>
 *   <li>Full OpenAPI annotations for auto-generated Swagger UI.</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/barley")
@RequiredArgsConstructor
@Tag(name = "Barley Inventory", description = "CRUD and analytics for barley inventory records")
@SecurityRequirement(name = "bearerAuth")
public class BarleyController {

    private final BarleyService barleyService;

    // ── POST /barley ───────────────────────────────────────────────────────

    @PostMapping
    @RateLimiter(name = "barleyApi")
    @Operation(
        summary  = "Create a barley inventory record",
        responses = {
            @ApiResponse(responseCode = "201", description = "Created successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden — ADMIN role required")
        }
    )
    public ResponseEntity<BarleyResponse> create(@Valid @RequestBody BarleyRequest request) {
        log.info("POST /barley name={}", request.name());
        BarleyResponse response = barleyService.create(request);

        // Return 201 with a Location header pointing to the new resource
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    // ── GET /barley/{id} ──────────────────────────────────────────────────

    @GetMapping("/{id}")
    @RateLimiter(name = "barleyApi")
    @Operation(
        summary  = "Get a barley record by ID",
        responses = {
            @ApiResponse(responseCode = "200", description = "Found"),
            @ApiResponse(responseCode = "404", description = "Not found",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
        }
    )
    public ResponseEntity<BarleyResponse> findById(
            @Parameter(description = "Barley record ID") @PathVariable Long id) {
        return ResponseEntity.ok(barleyService.findById(id));
    }

    // ── GET /barley ────────────────────────────────────────────────────────

    @GetMapping
    @RateLimiter(name = "barleyApi")
    @Operation(summary = "List all barley records (paginated)")
    public ResponseEntity<Page<BarleyResponse>> findAll(
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return ResponseEntity.ok(barleyService.findAll(pageable));
    }

    // ── GET /barley/search ─────────────────────────────────────────────────

    @GetMapping("/search")
    @RateLimiter(name = "barleyApi")
    @Operation(summary = "Filter barley records by variety, origin, stock status, and price range")
    public ResponseEntity<Page<BarleyResponse>> search(
            @RequestParam(required = false) String variety,
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(
                barleyService.findWithFilters(variety, origin, inStock, minPrice, maxPrice, pageable));
    }

    // ── GET /barley/stats ──────────────────────────────────────────────────

    @GetMapping("/stats")
    @RateLimiter(name = "barleyApi")
    @Operation(summary = "Get aggregated inventory statistics")
    public ResponseEntity<BarleyStatsResponse> getStats() {
        return ResponseEntity.ok(barleyService.getStats());
    }

    // ── PUT /barley/{id} ──────────────────────────────────────────────────

    @PutMapping("/{id}")
    @RateLimiter(name = "barleyApi")
    @Operation(
        summary  = "Update a barley record",
        responses = {
            @ApiResponse(responseCode = "200", description = "Updated successfully"),
            @ApiResponse(responseCode = "404", description = "Not found"),
            @ApiResponse(responseCode = "403", description = "Forbidden — ADMIN role required"),
            @ApiResponse(responseCode = "409", description = "Optimistic lock conflict — retry with fresh data")
        }
    )
    public ResponseEntity<BarleyResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody BarleyRequest request) {
        log.info("PUT /barley/{}", id);
        return ResponseEntity.ok(barleyService.update(id, request));
    }

    // ── DELETE /barley/{id} ───────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @RateLimiter(name = "barleyApi")
    @Operation(summary = "Delete a barley record")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("DELETE /barley/{}", id);
        barleyService.delete(id);
        return ResponseEntity.noContent().build();   // 204
    }

    // ── POST /barley/bulk-import ───────────────────────────────────────────

    @PostMapping("/bulk-import")
    @Operation(
        summary = "Async bulk import of barley records",
        description = "Processes all items in parallel on a background thread pool. " +
                      "Returns immediately with a future result — use /barley to poll for results."
    )
    public CompletableFuture<ResponseEntity<List<BarleyResponse>>> bulkImport(
            @Valid @RequestBody List<BarleyRequest> requests) {
        log.info("POST /barley/bulk-import {} items", requests.size());
        // Spring MVC unwraps CompletableFuture — the HTTP response is deferred until the future completes
        return barleyService.bulkImport(requests)
                .thenApply(results -> ResponseEntity.status(HttpStatus.CREATED).body(results));
    }

    // ── POST /barley/{id}/enrich ───────────────────────────────────────────

    @PostMapping("/{id}/enrich")
    @Operation(
        summary = "Async enrichment of a barley record from an external service",
        description = "Calls a downstream service with circuit-breaker, retry, and bulkhead protection."
    )
    public CompletableFuture<ResponseEntity<BarleyResponse>> enrich(@PathVariable Long id) {
        log.info("POST /barley/{}/enrich", id);
        return barleyService.enrichAsync(id)
                .thenApply(ResponseEntity::ok);
    }
}
