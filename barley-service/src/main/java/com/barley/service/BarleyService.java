package com.barley.service;

import com.barley.dto.BarleyRequest;
import com.barley.dto.BarleyResponse;
import com.barley.dto.BarleyStatsResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service contract for barley inventory operations.
 *
 * <p>Programming to an interface (rather than the concrete class) allows:
 * <ul>
 *   <li>Easy mocking in unit tests.</li>
 *   <li>Swapping implementations (e.g. a caching decorator, a stub for local dev).</li>
 *   <li>Proxy-based features like Spring's @Transactional and @Cacheable to work correctly
 *       (Spring wraps the interface in a JDK dynamic proxy).</li>
 * </ul>
 */
public interface BarleyService {

    BarleyResponse create(BarleyRequest request);

    BarleyResponse findById(Long id);

    Page<BarleyResponse> findAll(Pageable pageable);

    Page<BarleyResponse> findWithFilters(String variety, String origin,
            Boolean inStock, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);

    BarleyResponse update(Long id, BarleyRequest request);

    void delete(Long id);

    BarleyStatsResponse getStats();

    /** Async bulk import — returns a future so callers are not blocked. */
    CompletableFuture<List<BarleyResponse>> bulkImport(List<BarleyRequest> requests);

    /** Async enrichment from an external/downstream service. */
    CompletableFuture<BarleyResponse> enrichAsync(Long id);
}
