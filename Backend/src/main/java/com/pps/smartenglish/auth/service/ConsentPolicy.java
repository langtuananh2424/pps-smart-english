package com.pps.smartenglish.auth.service;

import com.pps.smartenglish.auth.dto.ConsentInput;
import com.pps.smartenglish.shared.error.ApiException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/** Kiểm tra ngày sinh và chấp thuận điều khoản khi tạo tài khoản (FR-AUTH-01, D-114, D-129). */
@Component
class ConsentPolicy {

    static final int ADULT_AGE = 18;
    private static final int MAX_AGE = 120;
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    /** Nội dung hai bộ điều khoản chờ pháp chế; đổi bản thì tăng số phiên bản. */
    static final String MINOR_TERMS = "minor-1";
    static final String ADULT_TERMS = "adult-1";

    record Consent(LocalDate dateOfBirth, String termsVersion) {}

    private final Clock clock;

    ConsentPolicy(Clock clock) {
        this.clock = clock;
    }

    Consent require(ConsentInput in) {
        LocalDate dob = in.dateOfBirth();
        if (dob == null) {
            throw ApiException.validation("dateOfBirth", "Ngày sinh là bắt buộc");
        }
        LocalDate today = LocalDate.now(clock.withZone(VN));
        if (dob.isAfter(today) || Period.between(dob, today).getYears() > MAX_AGE) {
            throw ApiException.validation("dateOfBirth", "Ngày sinh không hợp lệ");
        }
        boolean minor = Period.between(dob, today).getYears() < ADULT_AGE;
        if (minor && !Boolean.TRUE.equals(in.guardianConsent())) {
            throw ApiException.validation("guardianConsent", "Cần phụ huynh đồng ý điều khoản dành cho người dưới 18 tuổi");
        }
        if (!minor && !Boolean.TRUE.equals(in.termsAccepted())) {
            throw ApiException.validation("termsAccepted", "Cần đồng ý điều khoản sử dụng");
        }
        return new Consent(dob, minor ? MINOR_TERMS : ADULT_TERMS);
    }
}
