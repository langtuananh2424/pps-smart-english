package com.pps.smartenglish.placement.service;

import com.pps.smartenglish.placement.domain.PlacementAttempt;
import com.pps.smartenglish.placement.repository.PlacementAttemptRepository;
import com.pps.smartenglish.shared.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Ai được đụng vào lượt nào: chủ là user (nếu lượt đã có user) hoặc người giữ token (lượt của khách). */
@Component
class PlacementAccess {

    private final PlacementAttemptRepository attempts;

    PlacementAccess(PlacementAttemptRepository attempts) {
        this.attempts = attempts;
    }

    /** Khóa dòng rồi kiểm quyền. Lượt của user khác trả 404 để không lộ sự tồn tại; sai/thiếu token của khách trả 403. */
    PlacementAttempt lock(long id, Long userId, String token) {
        var a = attempts.lockById(id).orElseThrow(PlacementAccess::notFound);
        if (a.getUserId() != null) {
            if (!a.getUserId().equals(userId)) {
                throw notFound();
            }
            return a;
        }
        requireToken(a, token);
        return a;
    }

    static void requireToken(PlacementAttempt a, String token) {
        if (!PlacementTokens.matches(token, a.getGuestTokenHash())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "placement_token_invalid", "Thiếu hoặc sai mã của lượt làm bài");
        }
    }

    static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "attempt_not_found", "Không tìm thấy lượt làm bài");
    }
}
