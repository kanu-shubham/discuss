package com.barley.repository;

import com.barley.model.BarleyInventory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Data access layer for {@link BarleyInventory}.
 *
 * <p>Extends:
 * <ul>
 *   <li>{@link JpaRepository} — CRUD + pagination + sorting out of the box.</li>
 *   <li>{@link JpaSpecificationExecutor} — dynamic query building via the Criteria API
 *       (useful for complex filter UIs without string-concatenated JPQL).</li>
 * </ul>
 *
 * <p><b>Interview note:</b> Spring Data generates the SQL at startup from method names,
 * which eliminates boilerplate but still hits the DB — always layer caching above the repository.
 */
@Repository
public interface BarleyRepository
        extends JpaRepository<BarleyInventory, Long>, JpaSpecificationExecutor<BarleyInventory> {

    // ── Derived query methods (Spring Data generates JPQL) ─────────────────

    Page<BarleyInventory> findByVariety(String variety, Pageable pageable);

    Page<BarleyInventory> findByOrigin(String origin, Pageable pageable);

    Page<BarleyInventory> findByInStock(boolean inStock, Pageable pageable);

    List<BarleyInventory> findByHarvestYear(int harvestYear);

    Optional<BarleyInventory> findByNameIgnoreCase(String name);

    // ── Custom JPQL queries ────────────────────────────────────────────────

    @Query("""
            SELECT b FROM BarleyInventory b
            WHERE (:variety IS NULL OR b.variety = :variety)
              AND (:origin  IS NULL OR b.origin  = :origin)
              AND (:inStock IS NULL OR b.inStock = :inStock)
              AND b.pricePerKg BETWEEN :minPrice AND :maxPrice
            """)
    Page<BarleyInventory> findWithFilters(
            @Param("variety")  String variety,
            @Param("origin")   String origin,
            @Param("inStock")  Boolean inStock,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            Pageable pageable);

    // ── Aggregate / projection queries ────────────────────────────────────

    @Query("SELECT SUM(b.quantityKg) FROM BarleyInventory b WHERE b.inStock = true")
    BigDecimal sumTotalStockKg();

    @Query("SELECT AVG(b.pricePerKg) FROM BarleyInventory b")
    BigDecimal avgPricePerKg();

    @Query("SELECT COUNT(b) FROM BarleyInventory b WHERE b.inStock = false")
    long countOutOfStock();

    // ── Bulk update (bypasses entity load, very efficient) ────────────────

    @Modifying
    @Query("UPDATE BarleyInventory b SET b.inStock = :inStock WHERE b.id = :id")
    int updateStockStatus(@Param("id") Long id, @Param("inStock") boolean inStock);
}
