package com.barley.integration;

import com.barley.dto.BarleyRequest;
import com.barley.dto.BarleyResponse;
import com.barley.dto.AuthRequest;
import com.barley.dto.AuthResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration tests — starts the complete Spring Boot context.
 *
 * <p>{@code @SpringBootTest} + {@code @AutoConfigureMockMvc} boots a real application context
 * with an in-memory H2 database and Flyway migrations applied.
 *
 * <p>{@code @Transactional} rolls back each test's writes so tests are independent.
 *
 * <p><b>In a CI/CD pipeline:</b> swap H2 for a real PostgreSQL using Testcontainers
 * (already in pom.xml) so tests run against production-equivalent infrastructure:
 * <pre>
 *   &#64;Container
 *   static PostgreSQLContainer&lt;?&gt; postgres = new PostgreSQLContainer<>("postgres:16");
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Barley Service Integration Tests")
class BarleyIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String adminToken;

    @BeforeEach
    void obtainToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AuthRequest("admin", "admin123"))))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse auth = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        adminToken = "Bearer " + auth.token();
    }

    @Test
    @Order(1)
    @DisplayName("GET /barley returns seeded data")
    void getAll_returnsSeedData() throws Exception {
        mockMvc.perform(get("/barley")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5));  // from V1 migration
    }

    @Test
    @Order(2)
    @DisplayName("Full CRUD lifecycle for a barley record")
    void crudLifecycle() throws Exception {
        BarleyRequest req = BarleyRequest.builder()
                .name("Integration Test Barley")
                .variety("Six-Row")
                .origin("Canada")
                .quantityKg(new BigDecimal("100.000"))
                .pricePerKg(new BigDecimal("2.00"))
                .harvestYear(2024)
                .inStock(true)
                .build();

        // CREATE
        MvcResult createResult = mockMvc.perform(post("/barley")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn();

        BarleyResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), BarleyResponse.class);
        Long id = created.id();
        assertThat(id).isNotNull();

        // READ
        mockMvc.perform(get("/barley/" + id)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Integration Test Barley"));

        // UPDATE
        BarleyRequest updated = BarleyRequest.builder()
                .name("Updated Test Barley")
                .variety("Six-Row")
                .origin("Canada")
                .quantityKg(new BigDecimal("200.000"))
                .pricePerKg(new BigDecimal("2.50"))
                .harvestYear(2024)
                .inStock(true)
                .build();

        mockMvc.perform(put("/barley/" + id)
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Test Barley"))
                .andExpect(jsonPath("$.quantityKg").value(200.0));

        // DELETE
        mockMvc.perform(delete("/barley/" + id)
                        .header("Authorization", adminToken))
                .andExpect(status().isNoContent());

        // Verify deletion
        mockMvc.perform(get("/barley/" + id)
                        .header("Authorization", adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(3)
    @DisplayName("GET /barley/stats returns aggregated figures")
    void stats_returnsAggregates() throws Exception {
        mockMvc.perform(get("/barley/stats")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").isNumber())
                .andExpect(jsonPath("$.totalStockKg").isNumber())
                .andExpect(jsonPath("$.averagePricePerKg").isNumber());
    }

    @Test
    @Order(4)
    @DisplayName("Unauthenticated request returns 401")
    void unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/barley"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(5)
    @DisplayName("GET /actuator/health returns UP")
    void actuatorHealth_returnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
