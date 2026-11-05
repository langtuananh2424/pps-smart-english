package com.pps.smartenglish.auth.service;

import com.pps.smartenglish.auth.domain.User;
import com.pps.smartenglish.auth.domain.UserIdentity;
import com.pps.smartenglish.auth.repository.UserIdentityRepository;
import com.pps.smartenglish.auth.repository.UserRepository;
import com.pps.smartenglish.auth.service.IdentityTokenVerifier.ExternalIdentity;
import com.pps.smartenglish.shared.error.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Đăng nhập bằng nhà cung cấp ngoài (hiện có Google; FR-AUTH-01). */
@Service
public class OAuthService {

    private final List<IdentityTokenVerifier> verifiers;
    private final UserRepository users;
    private final UserIdentityRepository identities;
    private final AccountFactory accounts;
    private final SessionService sessions;
    private final TransactionTemplate tx;
    private final Clock clock;

    OAuthService(List<IdentityTokenVerifier> verifiers, UserRepository users, UserIdentityRepository identities,
                 AccountFactory accounts, SessionService sessions, TransactionTemplate tx, Clock clock) {
        this.verifiers = verifiers;
        this.users = users;
        this.identities = identities;
        this.accounts = accounts;
        this.sessions = sessions;
        this.tx = tx;
        this.clock = clock;
    }

    public LoginResult login(String provider, String idToken, String deviceLabel) {
        IdentityTokenVerifier verifier = verifiers.stream().filter(v -> provider.equals(v.provider())).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Không hỗ trợ nhà cung cấp này"));
        ExternalIdentity g = verifier.verify(idToken);
        User user = tx.execute(status -> resolveUser(provider, g));
        if (user.getStatus() == User.Status.LOCKED) {
            throw new ApiException(HttpStatus.FORBIDDEN, "account_locked", "Tài khoản đã bị khóa");
        }
        return sessions.open(user, deviceLabel);
    }

    private User resolveUser(String provider, ExternalIdentity g) {
        var existing = identities.findByProviderAndProviderSubject(provider, g.subject());
        if (existing.isPresent()) {
            return users.findById(existing.get().getUserId()).orElseThrow();
        }
        String email = g.email().trim().toLowerCase(Locale.ROOT);
        Instant now = clock.instant();
        User user = users.findByEmail(email).orElse(null);
        if (user == null) {
            user = accounts.createStudent(email, null, now, displayName(g, email));
        } else if (user.getEmailVerifiedAt() == null) {
            // Chặn kẻ đăng ký trước bằng email của người khác: bỏ mật khẩu và mọi phiên cũ trước khi nối.
            user.setPasswordHash(null);
            user.setEmailVerifiedAt(now);
            sessions.revokeAll(user.getId()); // flush hai thay đổi trên trước khi UPDATE
        }
        identities.save(new UserIdentity(user.getId(), provider, g.subject()));
        return user;
    }

    private static String displayName(ExternalIdentity g, String email) {
        String name = g.name() == null || g.name().isBlank() ? email.substring(0, email.indexOf('@')) : g.name().trim();
        return name.length() > 100 ? name.substring(0, 100) : name;
    }
}
