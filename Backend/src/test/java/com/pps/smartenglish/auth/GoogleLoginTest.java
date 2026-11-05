package com.pps.smartenglish.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.auth.service.IdentityTokenVerifier;
import com.pps.smartenglish.auth.service.IdentityTokenVerifier.ExternalIdentity;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.support.ApiTestBase;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class GoogleLoginTest extends ApiTestBase {

    @MockitoBean IdentityTokenVerifier verifier;

    @BeforeEach
    void stubProvider() {
        when(verifier.provider()).thenReturn("GOOGLE");
    }

    private String google(String subject, String email, String name) throws Exception {
        when(verifier.verify("tok")).thenReturn(new ExternalIdentity(subject, email, name));
        return post("/api/auth/oauth/google", Map.of("idToken", "tok"), newIp()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void newGoogleUserIsCreatedVerifiedWithoutPasswordAndWithZeroQuota() throws Exception {
        String email = newEmail();
        String body = google("sub-" + UUID.randomUUID(), email, "Gia Huy");
        long id = json.readTree(body).at("/user/id").asLong();

        assertThat(json.readTree(body).at("/user/displayName").asText()).isEqualTo("Gia Huy");
        assertThat(count("select count(*) from users where id = ? and password_hash is null and email_verified_at is not null", id)).isEqualTo(1);
        assertThat(count("select count(*) from user_profiles where user_id = ?", id)).isEqualTo(1);
        assertThat(count("select count(*) from user_ai_quota where user_id = ? and balance = 0", id)).isEqualTo(1);
        assertThat(count("select count(*) from user_identities where user_id = ? and provider = 'GOOGLE'", id)).isEqualTo(1);
    }

    @Test
    void missingNameFallsBackToEmailLocalPart() throws Exception {
        String body = google("sub-" + UUID.randomUUID(), "abc.def@example.com", null);
        assertThat(json.readTree(body).at("/user/displayName").asText()).isEqualTo("abc.def");
    }

    @Test
    void existingIdentityLogsInSameUserWithoutDuplicates() throws Exception {
        String sub = "sub-" + UUID.randomUUID();
        String email = newEmail();
        long first = json.readTree(google(sub, email, "A")).at("/user/id").asLong();
        long second = json.readTree(google(sub, email, "A")).at("/user/id").asLong();
        assertThat(second).isEqualTo(first);
        assertThat(count("select count(*) from user_identities where user_id = ?", first)).isEqualTo(1);
    }

    @Test
    void linksToVerifiedPasswordAccountKeepingPassword() throws Exception {
        Account a = register(newEmail(), "mat-khau-1");
        jdbc.update("update users set email_verified_at = CURRENT_TIMESTAMP where id = ?", a.id());
        long id = json.readTree(google("sub-" + UUID.randomUUID(), a.email(), "A")).at("/user/id").asLong();

        assertThat(id).isEqualTo(a.id());
        assertThat(count("select count(*) from users where id = ? and password_hash is not null", id)).isEqualTo(1);
        login(a.email(), "mat-khau-1");
    }

    @Test
    void linkingToUnverifiedAccountWipesPasswordAndSessionsAndVerifiesEmail() throws Exception {
        Account a = register(newEmail(), "mat-khau-1"); // kẻ đăng ký trước, chưa xác thực
        long id = json.readTree(google("sub-" + UUID.randomUUID(), a.email(), "Chủ thật")).at("/user/id").asLong();

        assertThat(id).isEqualTo(a.id());
        assertThat(count("select count(*) from users where id = ? and password_hash is null and email_verified_at is not null", id)).isEqualTo(1);
        assertThat(count("select count(*) from refresh_tokens where user_id = ? and revoked_at is null", id) ).isEqualTo(1); // chỉ phiên Google mới
        post("/api/auth/refresh", Map.of(), newIp(), new Cookie("refresh_token", a.refreshToken())).andExpect(status().isUnauthorized());
    }

    @Test
    void lockedUserIsForbidden() throws Exception {
        String sub = "sub-" + UUID.randomUUID();
        String email = newEmail();
        google(sub, email, "A");
        jdbc.update("update users set status = 'LOCKED' where email = ?", email);
        post("/api/auth/oauth/google", Map.of("idToken", "tok"), newIp()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("account_locked"));
    }

    @Test
    void invalidTokenIsUnauthorized() throws Exception {
        when(verifier.verify(anyString())).thenThrow(ApiException.unauthenticated());
        post("/api/auth/oauth/google", Map.of("idToken", "bad"), newIp()).andExpect(status().isUnauthorized());
    }
}
