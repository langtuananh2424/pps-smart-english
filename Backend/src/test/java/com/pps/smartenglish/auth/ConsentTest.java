package com.pps.smartenglish.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pps.smartenglish.auth.service.IdentityTokenVerifier;
import com.pps.smartenglish.auth.service.IdentityTokenVerifier.ExternalIdentity;
import com.pps.smartenglish.support.ApiTestBase;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** FR-AUTH-01, D-114, D-129: ngày sinh và chấp thuận khi đăng ký email và Google. */
class ConsentTest extends ApiTestBase {

    private static final String PASSWORD = "mat-khau-1";

    @MockitoBean IdentityTokenVerifier verifier;

    private Map<String, Object> body(Object dob, Object terms, Object guardian) {
        Map<String, Object> m = new HashMap<String, Object>(Map.of("email", newEmail(), "password", PASSWORD, "displayName", "A"));
        if (dob != null) m.put("dateOfBirth", dob);
        if (terms != null) m.put("termsAccepted", terms);
        if (guardian != null) m.put("guardianConsent", guardian);
        return m;
    }

    private void rejected(Map<String, Object> b, String field) throws Exception {
        post("/api/auth/register", b, newIp()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_failed"))
                .andExpect(jsonPath("$.error.details." + field).exists());
        assertThat(count("select count(*) from users where email = ?", b.get("email"))).isZero();
    }

    @Test
    void dateOfBirthIsRequiredAndMustBeReal() throws Exception {
        rejected(body(null, true, null), "dateOfBirth");
        rejected(body(LocalDate.now().plusDays(2).toString(), true, null), "dateOfBirth");
        rejected(body("1800-01-01", true, null), "dateOfBirth");
    }

    @Test
    void adultMustAcceptTerms() throws Exception {
        rejected(body("2000-01-01", null, null), "termsAccepted");
        rejected(body("2000-01-01", false, true), "termsAccepted");
    }

    @Test
    void minorNeedsGuardianTick() throws Exception {
        String dob = LocalDate.now().minusYears(15).toString();
        rejected(body(dob, true, null), "guardianConsent");
        rejected(body(dob, true, false), "guardianConsent");
    }

    @Test
    void adultIsStoredWithAdultTerms() throws Exception {
        var b = body("2000-01-01", true, null);
        post("/api/auth/register", b, newIp()).andExpect(status().isCreated());
        var row = jdbc.queryForMap("select date_of_birth, terms_version, terms_accepted_at from users where email = ?", b.get("email"));
        assertThat(row.get("TERMS_VERSION")).isEqualTo("adult-1");
        assertThat(row.get("TERMS_ACCEPTED_AT")).isNotNull();
        assertThat(row.get("DATE_OF_BIRTH").toString()).isEqualTo("2000-01-01");
    }

    @Test
    void minorWithGuardianTickIsStoredWithMinorTerms() throws Exception {
        var b = body(LocalDate.now().minusYears(15).toString(), null, true);
        post("/api/auth/register", b, newIp()).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select terms_version from users where email = ?", String.class, b.get("email")))
                .isEqualTo("minor-1");
    }

    @Test
    void eighteenthBirthdayTodayCountsAsAdult() throws Exception {
        var b = body(LocalDate.now().minusYears(18).toString(), true, null);
        post("/api/auth/register", b, newIp()).andExpect(status().isCreated());
    }

    @Test
    void googleNewAccountNeedsConsentButExistingOneDoesNot() throws Exception {
        when(verifier.provider()).thenReturn("GOOGLE");
        String sub = UUID.randomUUID().toString();
        String email = newEmail();
        when(verifier.verify("tok")).thenReturn(new ExternalIdentity(sub, email, "G"));
        post("/api/auth/oauth/google", Map.of("idToken", "tok"), newIp()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.dateOfBirth").exists());
        assertThat(count("select count(*) from users where email = ?", email)).isZero();

        post("/api/auth/oauth/google", Map.of("idToken", "tok", "dateOfBirth", "2000-01-01", "termsAccepted", true), newIp())
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select terms_version from users where email = ?", String.class, email)).isEqualTo("adult-1");

        post("/api/auth/oauth/google", Map.of("idToken", "tok"), newIp()).andExpect(status().isOk()); // lần sau không cần
    }

    private int count(String sql, Object arg) {
        return jdbc.queryForObject(sql, Integer.class, arg);
    }
}
