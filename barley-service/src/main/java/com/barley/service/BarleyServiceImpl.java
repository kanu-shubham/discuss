package com.barley.service;

import com.barley.config.CacheConfig;
import com.barley.dto.BarleyRequest;
import com.barley.dto.BarleyResponse;
import com.barley.dto.BarleyStatsResponse;
import com.barley.exception.BarleyNotFoundException;
import com.barley.model.BarleyInventory;
import com.barley.repository.BarleyRepository;
import com.barley.util.BarleyMapper;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Production-grade service implementation.
 *
 * <p><b>Patterns demonstrated:</b>
 * <ul>
 *   <li><b>@Transactional</b>   – ACID guarantees; readOnly=true for reads (no dirty-check overhead).</li>
 *   <li><b>@Cacheable / @CacheEvict / @CachePut</b> – cache-aside pattern managed declaratively.</li>
 *   <li><b>@Async</b>           – offloads slow work to a thread pool; caller gets a CompletableFuture.</li>
 *   <li><b>@CircuitBreaker</b>  – stops cascading failures from slow/down downstream services.</li>
 *   <li><b>@Retry</b>           – transparent retries with exponential back-off for transient errors.</li>
 *   <li><b>@Bulkhead</b>        – caps concurrent calls; prevents one slow path from exhausting threads.</li>
 *   <li><b>@Timed</b>           – Micrometer timer; metrics surfaced via /actuator/prometheus.</li>
 *   <li><b>@PreAuthorize</b>    – method-level security; enforced even if called internally.</li>
 *   <li><b>@Scheduled</b>       – periodic cache warm-up; keeps hot data ready without cold misses.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)   // default: all public methods are read-only transactions
public class BarleyServiceImpl implements BarleyService {

    private final BarleyRepository repository;
    private final BarleyMapper     mapper;
    private final MeterRegistry    meterRegistry;
    private final Executor         taskExecutor;

    // ── CREATE ────────────────────────────────────────────────────────────

    @Override
    @Transactional                // write operation — overrides class-level readOnly
    @PreAuthorize("hasRole('ADMIN')")
    @CacheEvict(cacheNames = CacheConfig.BARLEY_LIST_CACHE, allEntries = true)
    @Timed(value = "barley.create", description = "Time taken to create a barley record")
    public BarleyResponse create(BarleyRequest request) {
        log.info("Creating barley inventory record: name={}", request.name());

        BarleyInventory entity = mapper.toEntity(request);
        BarleyInventory saved  = repository.save(entity);

        meterRegistry.counter("barley.created.total").increment();
        log.info("Created barley record id={}", saved.getId());
        return mapper.toResponse(saved);
    }

    // ── READ ──────────────────────────────────────────────────────────────

    @Override
    @Cacheable(cacheNames = CacheConfig.BARLEY_CACHE, key = "#id")
    @Timed(value = "barley.find_by_id")
    public BarleyResponse findById(Long id) {
        log.debug("Fetching barley id={} (cache miss if you see this log)", id);
        return repository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new BarleyNotFoundException(id));
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.BARLEY_LIST_CACHE,
               key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort")
    @Timed(value = "barley.find_all")
    public Page<BarleyResponse> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::toResponse);
    }

    @Override
    @Timed(value = "barley.find_filtered")
    public Page<BarleyResponse> findWithFilters(
            String variety, String origin, Boolean inStock,
            BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {

        BigDecimal min = minPrice != null ? minPrice : BigDecimal.ZERO;
        BigDecimal max = maxPrice != null ? maxPrice : new BigDecimal("9999999.99");

        return repository.findWithFilters(variety, origin, inStock, min, max, pageable)
                         .map(mapper::toResponse);
    }

    // ── UPDATE ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    @Caching(
        put    = @CachePut(cacheNames = CacheConfig.BARLEY_CACHE, key = "#id"),
        evict  = @CacheEvict(cacheNames = CacheConfig.BARLEY_LIST_CACHE, allEntries = true)
    )
    @Timed(value = "barley.update")
    public BarleyResponse update(Long id, BarleyRequest request) {
        log.info("Updating barley id={}", id);
        BarleyInventory entity = repository.findById(id)
                .orElseThrow(() -> new BarleyNotFoundException(id));

        mapper.updateEntityFromRequest(request, entity);
        // No explicit save() needed: entity is managed, JPA dirty-checks on commit
        return mapper.toResponse(entity);
    }

    // ── DELETE ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    @Caching(evict = {
        @CacheEvict(cacheNames = CacheConfig.BARLEY_CACHE, key = "#id"),
        @CacheEvict(cacheNames = CacheConfig.BARLEY_LIST_CACHE, allEntries = true),
        @CacheEvict(cacheNames = CacheConfig.BARLEY_STATS_CACHE, allEntries = true)
    })
    @Timed(value = "barley.delete")
    public void delete(Long id) {
        log.info("Deleting barley id={}", id);
        if (!repository.existsById(id)) {
            throw new BarleyNotFoundException(id);
        }
        repository.deleteById(id);
        meterRegistry.counter("barley.deleted.total").increment();
    }

    // ── STATS ─────────────────────────────────────────────────────────────

    @Override
    @Cacheable(cacheNames = CacheConfig.BARLEY_STATS_CACHE, key = "'global'")
    @Timed(value = "barley.stats")
    public BarleyStatsResponse getStats() {
        return BarleyStatsResponse.builder()
                .totalItems(repository.count())
                .outOfStockCount(repository.countOutOfStock())
                .totalStockKg(repository.sumTotalStockKg())
                .averagePricePerKg(repository.avgPricePerKg())
                .build();
    }

    // ── ASYNC: Bulk Import ────────────────────────────────────────────────

    /**
     * Bulk import processes each item in parallel using CompletableFuture.
     *
     * <p><b>Interview talking points on CompletableFuture:</b>
     * <ul>
     *   <li>{@code supplyAsync} submits work to our thread pool and returns immediately.</li>
     *   <li>{@code allOf(...).join()} waits for ALL futures without blocking the HTTP thread
     *       (because this method itself is @Async — the HTTP thread returns a Future).</li>
     *   <li>{@code thenApply} chains transformations without additional threads.</li>
     *   <li>Exception handling: {@code exceptionally} is the async equivalent of a catch block.</li>
     * </ul>
     */
    @Override
    @Async("taskExecutor")   // runs on barley-async-N thread, not the HTTP thread
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    @Timed(value = "barley.bulk_import")
    public CompletableFuture<List<BarleyResponse>> bulkImport(List<BarleyRequest> requests) {
        log.info("Bulk import started: {} items on thread {}", requests.size(),
                Thread.currentThread().getName());

        Timer.Sample sample = Timer.start(meterRegistry);

        // Fan out: one future per item
        List<CompletableFuture<BarleyResponse>> futures = requests.stream()
                .map(req -> CompletableFuture
                        .supplyAsync(() -> {
                            BarleyInventory entity = mapper.toEntity(req);
                            return mapper.toResponse(repository.save(entity));
                        }, taskExecutor)
                        .exceptionally(ex -> {
                            log.error("Failed to import item '{}': {}", req.name(), ex.getMessage());
                            return null;   // skip failed items, don't abort the batch
                        }))
                .toList();

        // Fan in: wait for all, collect non-null results
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<BarleyResponse> results = futures.stream()
                .map(CompletableFuture::join)
                .filter(r -> r != null)
                .toList();

        sample.stop(meterRegistry.timer("barley.bulk_import.duration"));
        log.info("Bulk import complete: {}/{} items saved", results.size(), requests.size());

        return CompletableFuture.completedFuture(results);
    }

    // ── ASYNC: Enrich from external service ──────────────────────────────

    /**
     * Simulates enriching a record with data from an external service
     * (e.g. a market-price API, a weather API for harvest quality).
     *
     * <p>Resilience4j annotations stack: the call is first checked against the bulkhead
     * (max concurrent), then the circuit breaker, then the retry policy.
     */
    @Override
    @Async("taskExecutor")
    @CircuitBreaker(name = "barleyExternalService", fallbackMethod = "enrichFallback")
    @Retry(name = "barleyExternalService")
    @Bulkhead(name = "barleyService")
    @Timed(value = "barley.enrich_async")
    public CompletableFuture<BarleyResponse> enrichAsync(Long id) {
        log.info("Enriching barley id={} via external service on thread {}",
                id, Thread.currentThread().getName());

        BarleyInventory entity = repository.findById(id)
                .orElseThrow(() -> new BarleyNotFoundException(id));

        // Simulate external call latency
        simulateExternalCall();

        return CompletableFuture.completedFuture(mapper.toResponse(entity));
    }

    /** Circuit breaker fallback — called when the CB is open or retries exhausted. */
    public CompletableFuture<BarleyResponse> enrichFallback(Long id, Throwable t) {
        log.warn("Circuit open or retries exhausted for enrichAsync(id={}): {}", id, t.getMessage());
        // Return a degraded response (cached data, default values, or a meaningful error DTO)
        return CompletableFuture.completedFuture(findById(id));
    }

    // ── SCHEDULED: Cache Warm-up ─────────────────────────────────────────

    /**
     * Pre-warms the stats cache every 9 minutes so that the 10-minute expiry
     * never triggers a cold miss under traffic.
     *
     * <p>In production: use Quartz or a distributed scheduler (e.g., ShedLock) to prevent
     * every pod running this simultaneously.
     */
    @Scheduled(fixedDelay = 9 * 60 * 1000, initialDelay = 30_000)
    @CacheEvict(cacheNames = CacheConfig.BARLEY_STATS_CACHE, allEntries = true)
    public void warmStatsCache() {
        log.debug("Warming barley stats cache");
        getStats();
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private void simulateExternalCall() {
        try {
            Thread.sleep(50); // represents a real HTTP call to an external API
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("External call interrupted", e);
        }
    }
}
