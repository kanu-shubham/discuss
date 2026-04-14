package com.barley.service;

import com.barley.dto.BarleyRequest;
import com.barley.dto.BarleyResponse;
import com.barley.dto.BarleyStatsResponse;
import com.barley.exception.BarleyNotFoundException;
import com.barley.model.BarleyInventory;
import com.barley.repository.BarleyRepository;
import com.barley.util.BarleyMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BarleyServiceImpl}.
 *
 * <p>Uses Mockito to isolate the service from its dependencies (repository, mapper).
 * No Spring context is loaded — tests run in milliseconds.
 *
 * <p><b>AssertJ</b> is preferred over JUnit assertions for fluent, readable assertions.
 *
 * <p>Test structure uses {@code @Nested} classes to group related tests,
 * which produces a clear, hierarchical test report.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BarleyServiceImpl Unit Tests")
class BarleyServiceTest {

    @Mock  BarleyRepository repository;
    @Mock  BarleyMapper mapper;
    @Spy   MeterRegistry meterRegistry = new SimpleMeterRegistry();

    // Inline executor runs tasks on the calling thread (synchronous) for predictable tests
    Executor executor = Executors.newSingleThreadExecutor();

    BarleyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BarleyServiceImpl(repository, mapper, meterRegistry, executor);
    }

    // ── Test fixtures ───────────────────────────────────────────────────

    private BarleyInventory entity() {
        return BarleyInventory.builder()
                .id(1L)
                .name("Premium Malt Barley")
                .variety("Two-Row")
                .origin("Scotland")
                .quantityKg(new BigDecimal("5000.000"))
                .pricePerKg(new BigDecimal("1.25"))
                .harvestYear(2023)
                .inStock(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .version(0L)
                .build();
    }

    private BarleyResponse response(BarleyInventory e) {
        return new BarleyResponse(e.getId(), e.getName(), e.getVariety(), e.getOrigin(),
                e.getQuantityKg(), e.getPricePerKg(), e.getHarvestYear(),
                e.getInStock(), e.getCreatedAt(), e.getUpdatedAt());
    }

    private BarleyRequest request() {
        return BarleyRequest.builder()
                .name("Premium Malt Barley")
                .variety("Two-Row")
                .origin("Scotland")
                .quantityKg(new BigDecimal("5000.000"))
                .pricePerKg(new BigDecimal("1.25"))
                .harvestYear(2023)
                .inStock(true)
                .build();
    }

    // ── Tests ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("returns response when entity exists")
        void returnsResponseWhenFound() {
            BarleyInventory e = entity();
            when(repository.findById(1L)).thenReturn(Optional.of(e));
            when(mapper.toResponse(e)).thenReturn(response(e));

            BarleyResponse result = service.findById(1L);

            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.name()).isEqualTo("Premium Malt Barley");
            verify(repository, times(1)).findById(1L);
        }

        @Test
        @DisplayName("throws BarleyNotFoundException when entity absent")
        void throwsNotFoundWhenAbsent() {
            when(repository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById(99L))
                    .isInstanceOf(BarleyNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @DisplayName("returns paginated results")
        void returnsPaginatedResults() {
            BarleyInventory e = entity();
            Page<BarleyInventory> page = new PageImpl<>(List.of(e));
            when(repository.findAll(any(PageRequest.class))).thenReturn(page);
            when(mapper.toResponse(e)).thenReturn(response(e));

            Page<BarleyResponse> result = service.findAll(PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("saves entity and returns response")
        void savesEntityAndReturnsResponse() {
            BarleyRequest req = request();
            BarleyInventory e = entity();
            when(mapper.toEntity(req)).thenReturn(e);
            when(repository.save(e)).thenReturn(e);
            when(mapper.toResponse(e)).thenReturn(response(e));

            BarleyResponse result = service.create(req);

            assertThat(result.name()).isEqualTo("Premium Malt Barley");
            verify(repository, times(1)).save(e);
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("updates and returns response when entity exists")
        void updatesWhenFound() {
            BarleyInventory e = entity();
            BarleyRequest req = request();
            when(repository.findById(1L)).thenReturn(Optional.of(e));
            when(mapper.toResponse(e)).thenReturn(response(e));

            BarleyResponse result = service.update(1L, req);

            assertThat(result.id()).isEqualTo(1L);
            verify(mapper, times(1)).updateEntityFromRequest(req, e);
        }

        @Test
        @DisplayName("throws BarleyNotFoundException when entity absent")
        void throwsNotFoundWhenAbsent() {
            when(repository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(99L, request()))
                    .isInstanceOf(BarleyNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("deletes when entity exists")
        void deletesWhenFound() {
            when(repository.existsById(1L)).thenReturn(true);
            assertThatNoException().isThrownBy(() -> service.delete(1L));
            verify(repository, times(1)).deleteById(1L);
        }

        @Test
        @DisplayName("throws BarleyNotFoundException when entity absent")
        void throwsNotFoundWhenAbsent() {
            when(repository.existsById(99L)).thenReturn(false);
            assertThatThrownBy(() -> service.delete(99L))
                    .isInstanceOf(BarleyNotFoundException.class);
            verify(repository, never()).deleteById(any());
        }
    }

    @Nested
    @DisplayName("getStats")
    class GetStats {

        @Test
        @DisplayName("returns aggregated statistics")
        void returnsStats() {
            when(repository.count()).thenReturn(5L);
            when(repository.countOutOfStock()).thenReturn(1L);
            when(repository.sumTotalStockKg()).thenReturn(new BigDecimal("26500.5"));
            when(repository.avgPricePerKg()).thenReturn(new BigDecimal("1.54"));

            BarleyStatsResponse stats = service.getStats();

            assertThat(stats.totalItems()).isEqualTo(5L);
            assertThat(stats.outOfStockCount()).isEqualTo(1L);
            assertThat(stats.totalStockKg()).isEqualByComparingTo("26500.5");
        }
    }
}
