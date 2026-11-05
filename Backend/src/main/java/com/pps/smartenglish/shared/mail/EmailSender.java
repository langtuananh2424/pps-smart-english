package com.pps.smartenglish.shared.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private final JavaMailSender mail;
    private final String from;

    EmailSender(JavaMailSender mail, @Value("${app.mail.from}") String from) {
        this.mail = mail;
        this.from = from;
    }

    /** Lỗi gửi chỉ ghi log: tài khoản đã tạo, người dùng có thể bấm gửi lại. */
    public void send(String to, String subject, String text) {
        try {
            var msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(text);
            mail.send(msg);
        } catch (RuntimeException e) {
            log.warn("Gửi email thất bại: {}", e.toString());
        }
    }
}
