package com.pps.smartenglish.auth.service;

import com.pps.smartenglish.auth.domain.EmailToken.Purpose;

/** Nội dung thư theo mục đích token; thêm mục đích mới chỉ cần thêm một hằng ở đây. Link chỉ chứa token. */
enum MailTemplate {
    VERIFY("Xác thực email PPS Smart English", "/verify-email",
            "Vui lòng bấm vào liên kết dưới đây để xác thực email (hiệu lực 24 giờ):",
            "Nếu bạn không đăng ký, hãy bỏ qua thư này."),
    RESET("Đặt lại mật khẩu PPS Smart English", "/reset-password",
            "Bấm vào liên kết dưới đây để đặt lại mật khẩu (hiệu lực 30 phút):",
            "Nếu bạn không yêu cầu, hãy bỏ qua thư này.");

    private final String subject;
    private final String path;
    private final String intro;
    private final String outro;

    MailTemplate(String subject, String path, String intro, String outro) {
        this.subject = subject;
        this.path = path;
        this.intro = intro;
        this.outro = outro;
    }

    static MailTemplate of(Purpose purpose) {
        return valueOf(purpose.name());
    }

    String subject() {
        return subject;
    }

    String body(String baseUrl, String token) {
        return "Chào bạn,\n\n" + intro + "\n" + baseUrl + path + "?token=" + token + "\n\n" + outro;
    }
}
