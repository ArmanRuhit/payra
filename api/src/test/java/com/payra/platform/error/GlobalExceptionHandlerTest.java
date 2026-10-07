package com.payra.platform.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;



/**
 * 
 * GlobalExceptionHandlerTest
 * 
 * Contract test for the error wire format: RFC 7807 + always-present `code`.
 * Standalone MockMvc - no Spring context, no containers; this is a fast slice test
 * that proves exactly what a client sees on the wire.
 */
public class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FaultController())
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();


    @Test 
    void businessError_carriesCatalogueMetadata() throws Exception {
        mockMvc.perform(get("/test/business"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("https://payra.apps.armanruhit.dev/errors/resource-not-found"))
            .andExpect(jsonPath("$.title").value("Resource not found"))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
            .andExpect(jsonPath("$.detail").value("Wallet not found: 42"));
    }

    @Test
    void businessError_withoutDetail_leavesDetailAbsent() throws Exception {
        // code-only constructor: message already equals the title, detail adds nothing
        mockMvc.perform(get("/test/business-no-detail"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").doesNotExist());
    }

    @Test
    void validationError_listsFieldViolations() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("phone"))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    }

    @Test
    void missingResource_isMappedToNotFound() throws Exception {
        mockMvc.perform(get("/test/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void unexpectedError_becomesInternal_andLeaksNothing() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Something went wrong"))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").doesNotExist());
    }

    @RestController
    static class FaultController {

        @GetMapping("/test/business")
        void business() {
            throw new PayraException(ErrorCode.RESOURCE_NOT_FOUND, "Wallet not found: 42");
        }

        @GetMapping("/test/business-no-detail")
        void businessNoDetail() {
            throw new PayraException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        @PostMapping("/test/validate")
        void validate(@Valid @RequestBody PhoneRequest request) {
            // never reached - the body fails validation
        }

        @GetMapping("/test/missing")
        void missing() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/test/missing", "No static resource /test/missing");
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("jdbc:postgresql://host/db?password=hunter2");
        }
    }

    record PhoneRequest(@NotBlank String phone) {
    }
}
