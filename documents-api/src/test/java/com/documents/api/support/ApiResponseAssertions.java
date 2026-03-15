package com.documents.api.support;

import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

public final class ApiResponseAssertions {

    private ApiResponseAssertions() {
    }

    public static void assertErrorEnvelope(ResultActions result, String httpStatus, int code, String message) throws Exception {
        result.andExpect(MockMvcResultMatchers.status().is(HttpStatus.valueOf(httpStatus).value()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.httpStatus").value(httpStatus))
                .andExpect(MockMvcResultMatchers.jsonPath("$.success").value(false))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").value(message))
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(code))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data").doesNotExist());
    }
}
