package com.pps.smartenglish.auth.service;

import com.pps.smartenglish.shared.mail.EmailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Gửi thư sau commit. fallbackExecution: gửi ngay khi phát sự kiện ngoài transaction (gửi lại, quên mật khẩu). */
@Component
class AuthMailListener {

    private final EmailSender sender;
    private final String baseUrl;

    AuthMailListener(EmailSender sender, @Value("${app.base-url}") String baseUrl) {
        this.sender = sender;
        this.baseUrl = baseUrl;
    }

    @TransactionalEventListener(fallbackExecution = true, condition = "#e.purpose.name() == 'VERIFY'")
    public void onVerification(EmailTokenIssued e) {
        send(e);
    }

    /** Bất đồng bộ để thời gian phản hồi quên mật khẩu không lộ email có tồn tại hay không. */
    @Async
    @TransactionalEventListener(fallbackExecution = true, condition = "#e.purpose.name() == 'RESET'")
    public void onReset(EmailTokenIssued e) {
        send(e);
    }

    private void send(EmailTokenIssued e) {
        MailTemplate t = MailTemplate.of(e.purpose());
        sender.send(e.to(), t.subject(), t.body(baseUrl, e.token()));
    }
}
