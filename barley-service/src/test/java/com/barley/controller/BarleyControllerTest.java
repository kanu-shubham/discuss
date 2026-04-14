package com.barley.controller;

import com.barley.dto.BarleyRequest;
import com.barley.dto.BarleyResponse;
import com.barley.exception.BarleyNotFoundException;
import com.barley.service.BarleyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller slice tests using {@code @WebMvcTest}.
 *
 * <p>{@code @WebMvcTest} loads only the web layer (controllers, filters, advisors)
 * — the service layer is mocked with {@code @MockBean}. This is much faster
 * than loading the full {@code @SpringBootTest} context.
 *
 * <p>{@code MockMvc} fires real HTTP requests through the DispatcherServlet pipeline
 * without starting a real server.
 */
@WebMvcTest(BarleyController.class)
@DisplayName("BarleyController HTTP Layer Tests")
class BarleyControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean BarleyService barleyService;

    // ── Fixtures ────────────────────────────────────────────────────────

    private BarleyResponse sampleResponse() {
        return new BarleyResponse(1L, "Premium Malt Barley", "Two-Row", "Scotland",
                new BigDecimal("5000.000"), new BigDecimal("1.25"), 2023, true,
                LocalDateTime.now(), LocalDateTime.now());
    }

    private BarleyRequest sampleRequest() {
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
    @DisplayName("GET /barley/{id}")
    class GetById {

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("returns 200 with body when found")
        void returns200WhenFound() throws Exception {
            when(barleyService.findById(1L)).thenReturn(sampleResponse());

            mockMvc.perform(get("/barley/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value("Premium Malt Barley"))
                    .andExpect(jsonPath("$.variety").value("Two-Row"));
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("returns 404 when not found")
        void returns404WhenNotFound() throws Exception {
            when(barleyService.findById(99L)).thenThrow(new BarleyNotFoundException(99L));

            mockMvc.perform(get("/barley/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("returns 401 when unauthenticated")
        void returns401WhenUnauthenticated() throws Exception {
            mockMvc.perform(get("/barley/1"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /barley")
    class GetAll {

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("returns 200 with paginated list")
        void returns200WithPage() throws Exception {
            Page<BarleyResponse> page = new PageImpl<>(List.of(sampleResponse()));
            when(barleyService.findAll(any(Pageable.class))).thenReturn(page);

            mockMvc.perform(get("/barley?page=0&size=10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].name").value("Premium Malt Barley"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }
    }

    @Nested
    @DisplayName("POST /barley")
    class Create {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("returns 201 with Location header on valid request")
        void returns201OnValidRequest() throws Exception {
            when(barleyService.create(any(BarleyRequest.class))).thenReturn(sampleResponse());

            mockMvc.perform(post("/barley")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andExpect(jsonPath("$.id").value(1));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("returns 400 on validation failure")
        void returns400OnValidationFailure() throws Exception {
            BarleyRequest invalid = BarleyRequest.builder()
                    .name("")              // blank — violates @NotBlank
                    .variety("Two-Row")
                    .origin("Scotland")
                    .quantityKg(new BigDecimal("-1"))   // negative — violates @DecimalMin
                    .pricePerKg(new BigDecimal("1.25"))
                    .harvestYear(2023)
                    .build();

            mockMvc.perform(post("/barley")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors").isArray());
        }

        @Test
        @WithMockUser(roles = "USER")   // USER cannot create
        @DisplayName("returns 403 when USER role tries to create")
        void returns403ForUserRole() throws Exception {
            mockMvc.perform(post("/barley")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleRequest())))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("DELETE /barley/{id}")
    class Delete {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("returns 204 on successful delete")
        void returns204OnDelete() throws Exception {
            mockMvc.perform(delete("/barley/1").with(csrf()))
                    .andExpect(status().isNoContent());
        }
    }
}
