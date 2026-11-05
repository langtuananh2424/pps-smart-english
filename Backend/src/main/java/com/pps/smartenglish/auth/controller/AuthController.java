package com.pps.smartenglish.auth.controller;

import com.pps.smartenglish.auth.dto.AuthResponse;
import com.pps.smartenglish.auth.dto.ForgotPasswordRequest;
import com.pps.smartenglish.auth.dto.GoogleLoginRequest;
import com.pps.smartenglish.auth.dto.LoginRequest;
import com.pps.smartenglish.auth.dto.RegisterRequest;
import com.pps.smartenglish.auth.dto.ResetPasswordRequest;
import com.pps.smartenglish.auth.dto.SessionResponse;
import com.pps.smartenglish.auth.dto.TokenRequest;
import com.pps.smartenglish.auth.service.AccountRecoveryService;
import com.pps.smartenglish.auth.service.AuthService;
import com.pps.smartenglish.auth.service.OAuthService;
import com.pps.smartenglish.auth.service.SessionService;
import com.pps.smartenglish.shared.security.CurrentUser;
import com.pps.smartenglish.shared.web.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthService auth;
    private final OAuthService oauth;
    private final SessionService sessions;
    private final AccountRecoveryService recovery;

    AuthController(AuthService auth, OAuthService oauth, SessionService sessions, AccountRecoveryService recovery) {
        this.auth = auth;
        this.oauth = oauth;
        this.sessions = sessions;
        this.recovery = recovery;
    }

    @PostMapping("/register")
    ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req, HttpServletRequest http) {
        return RefreshCookies.session(HttpStatus.CREATED, auth.register(req, ClientIp.of(http), deviceLabel(http)));
    }

    @PostMapping("/login")
    ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return RefreshCookies.session(HttpStatus.OK, auth.login(req, ClientIp.of(http), deviceLabel(http)));
    }

    @PostMapping("/oauth/google")
    ResponseEntity<AuthResponse> google(@Valid @RequestBody GoogleLoginRequest req, HttpServletRequest http) {
        return RefreshCookies.session(HttpStatus.OK, oauth.login("GOOGLE", req.idToken(), deviceLabel(http)));
    }

    @PostMapping("/refresh")
    ResponseEntity<AuthResponse> refresh(@CookieValue(name = RefreshCookies.NAME, required = false) String token,
                                         HttpServletRequest http) {
        return RefreshCookies.session(HttpStatus.OK, sessions.refresh(token, deviceLabel(http)));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(name = RefreshCookies.NAME, required = false) String token) {
        sessions.logout(token);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, RefreshCookies.cleared().toString()).build();
    }

    @GetMapping("/sessions")
    List<SessionResponse> sessions(Authentication authn) {
        return sessions.list(CurrentUser.id(authn));
    }

    @DeleteMapping("/sessions/{id}")
    ResponseEntity<Void> revokeSession(Authentication authn, @PathVariable long id) {
        sessions.revoke(CurrentUser.id(authn), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-email")
    ResponseEntity<Void> verifyEmail(@Valid @RequestBody TokenRequest req) {
        recovery.verifyEmail(req.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resend-verification")
    ResponseEntity<Void> resend(Authentication authn) {
        recovery.resendVerification(CurrentUser.id(authn));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    ResponseEntity<Void> forgot(@Valid @RequestBody ForgotPasswordRequest req, HttpServletRequest http) {
        recovery.forgotPassword(req.email(), ClientIp.of(http));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/reset-password")
    ResponseEntity<Void> reset(@Valid @RequestBody ResetPasswordRequest req) {
        recovery.resetPassword(req.token(), req.newPassword());
        return ResponseEntity.noContent().build();
    }

    private static String deviceLabel(HttpServletRequest http) {
        String ua = http.getHeader(HttpHeaders.USER_AGENT);
        return ua == null ? null : ua.substring(0, Math.min(ua.length(), 255));
    }
}
