package com.barley.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA entity mapped to {@code barley_inventory} table.
 *
 * <p><b>Production patterns used here:</b>
 * <ul>
 *   <li><b>Optimistic locking</b> ({@code @Version}): instead of locking a DB row with SELECT FOR UPDATE,
 *       Hibernate checks the version column on UPDATE. If another transaction modified the row
 *       concurrently, it throws {@link jakarta.persistence.OptimisticLockException}, which the caller
 *       can retry. Zero contention on reads.</li>
 *   <li><b>Audit timestamps</b>: {@code @CreationTimestamp} / {@code @UpdateTimestamp} are set by
 *       Hibernate automatically — no manual date-setting in code.</li>
 *   <li><b>Sequence strategy</b>: {@code IDENTITY} works for H2/MySQL; switch to {@code SEQUENCE}
 *       with a named generator for PostgreSQL to support batch inserts without sacrificing performance.</li>
 * </ul>
 */
@Entity
@Table(
    name = "barley_inventory",
    indexes = {
        @Index(name = "idx_barley_variety",  columnList = "variety"),
        @Index(name = "idx_barley_origin",   columnList = "origin"),
        @Index(name = "idx_barley_in_stock", columnList = "in_stock")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"createdAt", "updatedAt"})
public class BarleyInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "variety", nullable = false, length = 100)
    private String variety;

    @Column(name = "origin", nullable = false, length = 100)
    private String origin;

    @Column(name = "quantity_kg", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantityKg;

    @Column(name = "price_per_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerKg;

    @Column(name = "harvest_year", nullable = false)
    private Integer harvestYear;

    @Column(name = "in_stock", nullable = false)
    @Builder.Default
    private Boolean inStock = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Optimistic locking version column.
     * Hibernate increments this on every UPDATE.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
