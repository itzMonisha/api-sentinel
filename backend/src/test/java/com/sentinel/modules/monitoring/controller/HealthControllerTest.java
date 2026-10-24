package com.sentinel.modules.monitoring.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/v1/health returns 200 OK with application uptime and status")
    void shouldReturnHealthStatusSuccessfully() throws Exception {
        mockMvc.perform(get("/api/v1/health")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("operational")))
                .andExpect(jsonPath("$.data.status", anyOf(is("UP"), is("DEGRADED"))))
                .andExpect(jsonPath("$.data.application", is("api-sentinel")))
                .andExpect(jsonPath("$.data.environment", is("test")))
                .andExpect(jsonPath("$.data.timestamp", notNullValue()))
                .andExpect(jsonPath("$.data.uptimeSeconds", greaterThanOrEqualTo(0)));
    }

    @Test
    @DisplayName("GET /actuator/health is publicly accessible and returns 200 OK")
    void shouldAccessActuatorHealthPublicly() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", notNullValue()));
    }

    @Test
    @DisplayName("GET /actuator/metrics requires authentication and returns 401 Unauthorized")
    void shouldRestrictActuatorMetricsWithoutAuth() throws Exception {
        mockMvc.perform(get("/actuator/metrics")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.errorCode", is("UNAUTHORIZED")));
    }
}
