package com.pps.smartenglish.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.support.ApiTestBase;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SessionTest extends ApiTestBase {

    private static final String PASSWORD = "mat-khau-1";

    private static Cookie cookie(String token) {
        return new Cookie("refresh_token", token);
    }

    private int activeRefreshTokens(long userId) {
        return count("select count(*) from refresh_tokens where user_id = ? and revoked_at is null", userId);
    }

    @Test
    void refreshRotatesTokenAndIssuesNewCookie() throws Exception {
        Account a = register(newEmail(), PASSWORD);
        var res = post("/api/auth/refresh", Map.of(), newIp(), cookie(a.refreshToken())).andExpect(status().isOk())
                .andReturn().getResponse();
        String rotated = res.getCookie("refresh_token").getValue();
        assertThat(rotated).isNotEqualTo(a.refreshToken());
        assertThat(activeRefreshTokens(a.id())).isEqualTo(1);
        post("/api/auth/refresh", Map.of(), newIp(), cookie(rotated)).andExpect(status().isOk());
    }

    @Test
    void reusingRotatedTokenRevokesEverySessionAndIsCommitted() throws Exception {
        Account a = register(newEmail(), PASSWORD);
        String newest = post("/api/auth/refresh", Map.of(), newIp(), cookie(a.refreshToken())).andReturn().getResponse()
                .getCookie("refresh_token").getValue();
        login(a.email(), PASSWORD); // phiên thứ hai trên thiết bị khác
        assertThat(activeRefreshTokens(a.id())).isEqualTo(2);
        // quá ân hạn 10 giây mới bị coi là dùng lại
        jdbc.update("update refresh_tokens set replaced_at = ? where token_hash is not null and user_id = ? and replaced_at is not null",
                java.time.OffsetDateTime.now().minusSeconds(60), a.id());

        post("/api/auth/refresh", Map.of(), newIp(), cookie(a.refreshToken())).andExpect(status().isUnauthorized());

        // Test không bọc transaction nên đọc được là đã commit thật.
        assertThat(activeRefreshTokens(a.id())).isZero();
        post("/api/auth/refresh", Map.of(), newIp(), cookie(newest)).andExpect(status().isUnauthorized());
    }

    @Test
    void revokedSessionReplayedDoesNotKillOtherSessions() throws Exception {
        Account a = register(newEmail(), PASSWORD);
        Account b = login(a.email(), PASSWORD);
        long aSession = jdbc.queryForObject("select min(id) from refresh_tokens where user_id = ?", Long.class, a.id());
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/auth/sessions/" + aSession),
                b.accessToken()).andExpect(status().isNoContent());

        post("/api/auth/refresh", Map.of(), newIp(), cookie(a.refreshToken())).andExpect(status().isUnauthorized());
        post("/api/auth/refresh", Map.of(), newIp(), cookie(b.refreshToken())).andExpect(status().isOk());
    }

    @Test
    void loggedOutSessionReplayedDoesNotKillOtherSessions() throws Exception {
        Account a = register(newEmail(), PASSWORD);
        Account b = login(a.email(), PASSWORD);
        post("/api/auth/logout", Map.of(), newIp(), cookie(a.refreshToken())).andExpect(status().isNoContent());

        post("/api/auth/refresh", Map.of(), newIp(), cookie(a.refreshToken())).andExpect(status().isUnauthorized());
        post("/api/auth/refresh", Map.of(), newIp(), cookie(b.refreshToken())).andExpect(status().isOk());
    }

    @Test
    void replayWithinGraceRejectsButKeepsNewToken() throws Exception {
        Account a = register(newEmail(), PASSWORD);
        String newest = post("/api/auth/refresh", Map.of(), newIp(), cookie(a.refreshToken())).andReturn().getResponse()
                .getCookie("refresh_token").getValue();

        post("/api/auth/refresh", Map.of(), newIp(), cookie(a.refreshToken())).andExpect(status().isUnauthorized());
        assertThat(activeRefreshTokens(a.id())).isEqualTo(1);
        post("/api/auth/refresh", Map.of(), newIp(), cookie(newest)).andExpect(status().isOk());
    }

    @Test
    void refreshRejectsMissingUnknownAndExpiredTokens() throws Exception {
        post("/api/auth/refresh", Map.of(), newIp()).andExpect(status().isUnauthorized());
        post("/api/auth/refresh", Map.of(), newIp(), cookie("khong-ton-tai")).andExpect(status().isUnauthorized());
        Account a = register(newEmail(), PASSWORD);
        expireTokens("refresh_tokens", a.id());
        post("/api/auth/refresh", Map.of(), newIp(), cookie(a.refreshToken())).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshForLockedUserIsForbidden() throws Exception {
        Account a = register(newEmail(), PASSWORD);
        jdbc.update("update users set status = 'LOCKED' where id = ?", a.id());
        post("/api/auth/refresh", Map.of(), newIp(), cookie(a.refreshToken())).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("account_locked"));
    }

    @Test
    void logoutRevokesTokenClearsCookieAndToleratesMissingCookie() throws Exception {
        Account a = register(newEmail(), PASSWORD);
        var res = post("/api/auth/logout", Map.of(), newIp(), cookie(a.refreshToken())).andExpect(status().isNoContent())
                .andReturn().getResponse();
        assertThat(res.getCookie("refresh_token").getMaxAge()).isZero();
        assertThat(activeRefreshTokens(a.id())).isZero();
        post("/api/auth/logout", Map.of(), newIp()).andExpect(status().isNoContent());
    }

    @Test
    void sessionsListAndRevokeOnlyOwn() throws Exception {
        Account a = register(newEmail(), PASSWORD);
        Account b = register(newEmail(), PASSWORD);
        login(a.email(), PASSWORD);

        send(get("/api/auth/sessions"), a.accessToken()).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").exists()).andExpect(jsonPath("$[0].expiresAt").exists());

        long bSession = jdbc.queryForObject("select id from refresh_tokens where user_id = ?", Long.class, b.id());
        send(delete("/api/auth/sessions/" + bSession), a.accessToken()).andExpect(status().isNotFound());
        assertThat(activeRefreshTokens(b.id())).isEqualTo(1);

        long aSession = jdbc.queryForObject("select min(id) from refresh_tokens where user_id = ?", Long.class, a.id());
        send(delete("/api/auth/sessions/" + aSession), a.accessToken()).andExpect(status().isNoContent());
        send(get("/api/auth/sessions"), a.accessToken()).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void sessionsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/auth/sessions")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("unauthenticated"));
    }
}
