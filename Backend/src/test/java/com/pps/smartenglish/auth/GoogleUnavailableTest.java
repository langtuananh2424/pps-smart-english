package com.pps.smartenglish.auth;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.support.ApiTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Profile test không đặt GOOGLE_CLIENT_ID: app vẫn khởi động, endpoint trả 503. */
class GoogleUnavailableTest extends ApiTestBase {

    @Test
    void returns503WhenClientIdMissing() throws Exception {
        post("/api/auth/oauth/google", Map.of("idToken", "x"), newIp()).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("google_login_unavailable"));
    }
}
