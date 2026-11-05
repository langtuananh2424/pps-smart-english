package com.pps.smartenglish.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.support.ApiTestBase;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EmailFlowTest extends ApiTestBase {

    private static final String PASSWORD = "mat-khau-1";

    private boolean verified(long id) {
        return count("select count(*) from users where id = ? and email_verified_at is not null", id) == 1;
    }

    @Test
    void verifyEmailWorksOnceOnly() throws Exception {
        Account a = register(newEmail(), PASSWORD);
        String token = verificationTokenFor(a.email());
        assertThat(verified(a.id())).isFalse();

        post("/api/auth/verify-email", Map.of("token", token), newIp()).andExpect(status().isNoContent());
        assertThat(verified(a.id())).isTrue();
        post("/api/auth/verify-email", Map.of("token", token), newIp()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_token"));
    }

    @Test
    void verifyEmailRejectsUnknownAndExpiredTokens() throws Exception {
        post("/api/auth/verify-email", Map.of("token", "khong-ton-tai"), newIp()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_token"));
        Account a = register(newEmail(), PASSWORD);
        String token = verificationTokenFor(a.email());
        expireTokens("email_tokens", a.id());
        post("/api/auth/verify-email", Map.of("token", token), newIp()).andExpect(status().isBadRequest());
        assertThat(verified(a.id())).isFalse();
    }

    @Test
    void resendIsLimitedToThreePerHourAndSkippedWhenVerified() throws Exception {
        Account a = register(newEmail(), PASSWORD);
        for (int i = 0; i < 3; i++) {
            sendAuthed("/api/auth/resend-verification", a).andExpect(status().isNoContent());
        }
        sendAuthed("/api/auth/resend-verification", a).andExpect(status().isTooManyRequests());

        Account b = register(newEmail(), PASSWORD);
        post("/api/auth/verify-email", Map.of("token", verificationTokenFor(b.email())), newIp());
        sendAuthed("/api/auth/resend-verification", b).andExpect(status().isNoContent());
        verify(sender, org.mockito.Mockito.times(1)).send(eq(b.email()), anyString(), contains("verify-email")); // chỉ thư lúc đăng ký
    }

    @Test
    void forgotPasswordAlwaysAcceptedAndMailsOnlyExistingAccounts() throws Exception {
        Account a = register(newEmail(), PASSWORD);
        post("/api/auth/forgot-password", Map.of("email", a.email()), newIp()).andExpect(status().isAccepted());
        assertThat(resetTokenFor(a.email())).isNotBlank();

        String unknown = newEmail();
        post("/api/auth/forgot-password", Map.of("email", unknown), newIp()).andExpect(status().isAccepted());
        verify(sender, never()).send(eq(unknown), anyString(), anyString());

        post("/api/auth/forgot-password", Map.of("email", "khong-hop-le"), newIp()).andExpect(status().isBadRequest());
    }

    @Test
    void forgotPasswordOverLimitStaysAcceptedButSendsNothing() throws Exception {
        Account a = register(newEmail(), PASSWORD);
        for (int i = 0; i < 5; i++) {
            post("/api/auth/forgot-password", Map.of("email", a.email()), newIp()).andExpect(status().isAccepted());
        }
        verify(sender, org.mockito.Mockito.timeout(2000).times(3)).send(eq(a.email()), anyString(), contains("reset-password"));
    }

    @Test
    void resetPasswordChangesPasswordRevokesSessionsVerifiesEmailAndIsSingleUse() throws Exception {
        Account a = register(newEmail(), PASSWORD);
        post("/api/auth/forgot-password", Map.of("email", a.email()), newIp());
        String token = resetTokenFor(a.email());

        post("/api/auth/reset-password", Map.of("token", token, "newPassword", "mat-khau-moi-2"), newIp())
                .andExpect(status().isNoContent());

        assertThat(count("select count(*) from refresh_tokens where user_id = ? and revoked_at is null", a.id())).isZero();
        assertThat(verified(a.id())).isTrue();
        post("/api/auth/refresh", Map.of(), newIp(), new Cookie("refresh_token", a.refreshToken()))
                .andExpect(status().isUnauthorized());
        post("/api/auth/login", Map.of("identifier", a.email(), "password", "mat-khau-moi-2"), newIp()).andExpect(status().isOk());
        post("/api/auth/login", Map.of("identifier", a.email(), "password", PASSWORD), newIp()).andExpect(status().isUnauthorized());
        post("/api/auth/reset-password", Map.of("token", token, "newPassword", "mat-khau-moi-3"), newIp())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("invalid_token"));
    }

    @Test
    void resetPasswordRejectsExpiredTokenAndWeakPasswordWithoutBurningToken() throws Exception {
        Account a = register(newEmail(), PASSWORD);
        post("/api/auth/forgot-password", Map.of("email", a.email()), newIp());
        String token = resetTokenFor(a.email());

        post("/api/auth/reset-password", Map.of("token", token, "newPassword", "ngan"), newIp()).andExpect(status().isBadRequest());
        expireTokens("email_tokens", a.id());
        post("/api/auth/reset-password", Map.of("token", token, "newPassword", "mat-khau-moi-2"), newIp())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("invalid_token"));
    }

    private org.springframework.test.web.servlet.ResultActions sendAuthed(String url, Account a) throws Exception {
        return send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url), a.accessToken());
    }
}
