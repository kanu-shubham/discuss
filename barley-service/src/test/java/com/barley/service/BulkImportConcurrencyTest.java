package com.barley.service;

import com.barley.dto.BarleyRequest;
import com.barley.dto.BarleyResponse;
import com.barley.model.BarleyInventory;
import com.barley.repository.BarleyRepository;
import com.barley.util.BarleyMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Concurrency test — verifies that bulkImport works correctly under parallel execution.
 *
 * <p>These tests check:
 * <ul>
 *   <li>All items are processed (no dropped futures).</li>
 *   <li>Failed items are skipped without aborting the batch.</li>
 *   <li>The CompletableFuture resolves correctly after fan-out + fan-in.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Bulk Import Concurrency Tests")
class BulkImportConcurrencyTest {

    @Mock BarleyRepository repository;
    @Mock BarleyMapper mapper;

    BarleyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BarleyServiceImpl(
                repository, mapper, new SimpleMeterRegistry(),
                ForkJoinPool.commonPool());
    }

    @Test
    @DisplayName("bulk import of 50 items succeeds concurrently")
    void bulkImport50Items() throws Exception {
        List<BarleyRequest> requests = IntStream.rangeClosed(1, 50)
                .mapToObj(i -> BarleyRequest.builder()
                        .name("Barley " + i)
                        .variety("Two-Row")
                        .origin("Scotland")
                        .quantityKg(BigDecimal.TEN)
                        .pricePerKg(BigDecimal.ONE)
                        .harvestYear(2023)
                        .inStock(true)
                        .build())
                .toList();

        BarleyInventory entity = BarleyInventory.builder()
                .id(1L).name("x").variety("y").origin("z")
                .quantityKg(BigDecimal.TEN).pricePerKg(BigDecimal.ONE)
                .harvestYear(2023).inStock(true)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).version(0L)
                .build();

        BarleyResponse response = new BarleyResponse(1L, "x", "y", "z",
                BigDecimal.TEN, BigDecimal.ONE, 2023, true,
                LocalDateTime.now(), LocalDateTime.now());

        when(mapper.toEntity(any())).thenReturn(entity);
        when(repository.save(any())).thenReturn(entity);
        when(mapper.toResponse(entity)).thenReturn(response);

        CompletableFuture<List<BarleyResponse>> future = service.bulkImport(requests);
        List<BarleyResponse> results = future.get();   // blocks until complete

        assertThat(results).hasSize(50);
    }

    @Test
    @DisplayName("bulk import skips failed items and processes the rest")
    void bulkImportSkipsFailedItems() throws Exception {
        List<BarleyRequest> requests = IntStream.rangeClosed(1, 5)
                .mapToObj(i -> BarleyRequest.builder()
                        .name("Barley " + i)
                        .variety("Two-Row")
                        .origin("Scotland")
                        .quantityKg(BigDecimal.TEN)
                        .pricePerKg(BigDecimal.ONE)
                        .harvestYear(2023)
                        .inStock(true)
                        .build())
                .toList();

        BarleyInventory entity = BarleyInventory.builder()
                .id(1L).name("x").variety("y").origin("z")
                .quantityKg(BigDecimal.TEN).pricePerKg(BigDecimal.ONE)
                .harvestYear(2023).inStock(true)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).version(0L)
                .build();

        BarleyResponse response = new BarleyResponse(1L, "x", "y", "z",
                BigDecimal.TEN, BigDecimal.ONE, 2023, true,
                LocalDateTime.now(), LocalDateTime.now());

        when(mapper.toEntity(any())).thenReturn(entity);
        // First call throws, rest succeed
        when(repository.save(any()))
                .thenThrow(new RuntimeException("DB error on first item"))
                .thenReturn(entity)
                .thenReturn(entity)
                .thenReturn(entity)
                .thenReturn(entity);
        when(mapper.toResponse(entity)).thenReturn(response);

        List<BarleyResponse> results = service.bulkImport(requests).get();

        // 1 failed, 4 succeeded
        assertThat(results).hasSize(4);
    }
}
