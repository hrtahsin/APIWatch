package com.hasan.apiwatch.controller;

import com.hasan.apiwatch.exception.GlobalExceptionHandler;
import com.hasan.apiwatch.service.ServiceMonitorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MonitoredServiceControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MonitoredServiceController(mock(ServiceMonitorService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void rejectsInvalidServiceRegistration() throws Exception {
        mockMvc.perform(post("/api/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "url": "not-a-url",
                                  "expectedStatusCode": 99,
                                  "timeoutMs": 10,
                                  "failureThreshold": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.url").exists())
                .andExpect(jsonPath("$.fieldErrors.expectedStatusCode").exists())
                .andExpect(jsonPath("$.fieldErrors.timeoutMs").exists())
                .andExpect(jsonPath("$.fieldErrors.failureThreshold").exists());
    }

    @Test
    void acceptsHttpsServiceUrl() throws Exception {
        mockMvc.perform(post("/api/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "GitHub API",
                                  "url": "https://api.github.com",
                                  "expectedStatusCode": 200,
                                  "timeoutMs": 3000,
                                  "failureThreshold": 3
                                }
                                """))
                .andExpect(status().isCreated());
    }
}
