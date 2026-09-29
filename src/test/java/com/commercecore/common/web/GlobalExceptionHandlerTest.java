package com.commercecore.common.web;

import com.commercecore.testsupport.ErrorDemoTestController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the standard {@link ApiError} shape for the cases required
 * by Phase 1: validation errors, not-found style application
 * exceptions, and unexpected server errors. Security filters are
 * disabled for this focused slice test -- it is testing error
 * handling, not authorization (that's covered separately for the
 * permitted infrastructure endpoints).
 */
@WebMvcTest(controllers = ErrorDemoTestController.class)
@AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void validationFailure_returnsStandardErrorShapeWithFieldErrors() throws Exception {
        String badPayload = objectMapper.writeValueAsString(
                new ErrorDemoTestController.Payload("", "not-an-email"));

        mockMvc.perform(post("/__test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.path").value("/__test/validate"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
    }

    @Test
    void malformedRequestBody_returnsStandardErrorShape() throws Exception {
        mockMvc.perform(post("/__test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void notFoundException_returns404WithStandardErrorShape() throws Exception {
        mockMvc.perform(get("/__test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Demo resource not found."));
    }

    @Test
    void unexpectedException_returns500WithoutLeakingInternals() throws Exception {
        mockMvc.perform(get("/__test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("IllegalStateException")),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Demo unexpected failure")),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("ErrorDemoTestController")),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("stackTrace")))));
    }
}
