package com.pps.smartenglish.auth;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.support.ApiTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

/** D-94: refresh token đi qua body khi client gửi X-Client: mobile; web giữ cookie. */
class MobileRefreshTest extends ApiTestBase {

    private static final String PASSWORD = "mat-khau-1";

    private MockHttpServletResponse mobile(String url, Object body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))
                .header("X-Real-IP", newIp()).header("X-Client", "MOBILE")).andReturn().getResponse();
    }

    private String mobileLogin(String email) throws Exception {
        MockHttpServletResponse res = mobile("/api/auth/login", Map.of("identifier", email, "password", PASSWORD));
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getCookie("refresh_token")).isNull();
        return json.readTree(res.getContentAsString()).path("refreshToken").asText();
    }

    @Test
    void mobileLoginReturnsRefreshTokenInBody() throws Exception {
        Account a = register(newEmail(), PASSWORD);
        assertThat(mobileLogin(a.email())).isNotBlank();
    }

    @Test
    void mobileRegisterReturnsRefreshTokenInBody() throws Exception {
        MockHttpServletResponse res = mobile("/api/auth/register",
                Map.of("email", newEmail(), "password", PASSWORD, "displayName", "Học viên", "dateOfBirth", "2000-01-01", "termsAccepted", true));
        assertThat(res.getStatus()).isEqualTo(201);
        assertThat(res.getCookie("refresh_token")).isNull();
        assertThat(json.readTree(res.getContentAsString()).path("refreshToken").asText()).isNotBlank();
    }

    @Test
    void refreshWithBodyRotatesAndReuseFails() throws Exception {
        Account a = register(newEmail(), PASSWORD);
        String first = mobileLogin(a.email());
        MockHttpServletResponse res = mobile("/api/auth/refresh", Map.of("refreshToken", first));
        assertThat(res.getStatus()).isEqualTo(200);
        String second = json.readTree(res.getContentAsString()).path("refreshToken").asText();
        assertThat(second).isNotBlank().isNotEqualTo(first);

        assertThat(mobile("/api/auth/refresh", Map.of("refreshToken", first)).getStatus()).isEqualTo(401);
        assertThat(mobile("/api/auth/refresh", Map.of("refreshToken", second)).getStatus()).isEqualTo(200);
    }

    @Test
    void logoutWithBodyRevokesToken() throws Exception {
        Account a = register(newEmail(), PASSWORD);
        String token = mobileLogin(a.email());
        assertThat(mobile("/api/auth/logout", Map.of("refreshToken", token)).getStatus()).isEqualTo(204);
        assertThat(mobile("/api/auth/refresh", Map.of("refreshToken", token)).getStatus()).isEqualTo(401);
    }

    @Test
    void webResponseHasNoRefreshTokenInBody() throws Exception {
        Account a = register(newEmail(), PASSWORD);
        var res = post("/api/auth/login", Map.of("identifier", a.email(), "password", PASSWORD), newIp())
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(json.readTree(res.getContentAsString()).has("refreshToken")).isFalse();
        assertThat(res.getCookie("refresh_token")).isNotNull();
    }
}
