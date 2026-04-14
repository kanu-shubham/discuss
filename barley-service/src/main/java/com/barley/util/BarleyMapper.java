package com.barley.util;

import com.barley.dto.BarleyRequest;
import com.barley.dto.BarleyResponse;
import com.barley.model.BarleyInventory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * MapStruct mapper — generates byte-code at compile time (zero reflection cost at runtime).
 *
 * <p><b>Interview talking points:</b>
 * <ul>
 *   <li>MapStruct vs ModelMapper: MapStruct is compile-time (fast, type-safe); ModelMapper is
 *       runtime reflection (flexible but slow and error-prone).</li>
 *   <li>componentModel = "spring" means the generated impl is a Spring @Component, so you
 *       can @Autowire it anywhere.</li>
 *   <li>@Mapping(target="id", ignore=true) ensures inbound requests cannot set the DB primary key.</li>
 * </ul>
 */
@Mapper(componentModel = "spring")
public interface BarleyMapper {

    /**
     * Maps a request DTO to a new entity (used on CREATE).
     * id, createdAt, updatedAt, version are ignored — managed by DB/Hibernate.
     */
    @Mapping(target = "id",        ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version",   ignore = true)
    @Mapping(target = "inStock",   source = "inStock", defaultValue = "true")
    BarleyInventory toEntity(BarleyRequest request);

    /** Maps an entity to a response DTO (used on READ). */
    BarleyResponse toResponse(BarleyInventory entity);

    /**
     * Updates an existing entity in-place from a request DTO (used on UPDATE).
     * @MappingTarget tells MapStruct to modify the existing instance rather than create a new one.
     */
    @Mapping(target = "id",        ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version",   ignore = true)
    void updateEntityFromRequest(BarleyRequest request, @MappingTarget BarleyInventory entity);
}
