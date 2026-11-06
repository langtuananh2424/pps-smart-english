package com.pps.smartenglish.retention.controller;

import com.pps.smartenglish.retention.config.RetentionProperties;
import com.pps.smartenglish.retention.dto.PushDtos.Subscribed;
import com.pps.smartenglish.retention.dto.PushDtos.SubscribeRequest;
import com.pps.smartenglish.retention.dto.PushDtos.UnsubscribeRequest;
import com.pps.smartenglish.retention.dto.PushDtos.VapidKey;
import com.pps.smartenglish.retention.service.PushSubscriptionService;
import com.pps.smartenglish.shared.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** FR-RET-03: đăng ký và hủy đầu nhận Web Push, lấy khóa công khai VAPID. */
@RestController
class PushController {

    private final PushSubscriptionService subscriptions;
    private final RetentionProperties props;

    PushController(PushSubscriptionService subscriptions, RetentionProperties props) {
        this.subscriptions = subscriptions;
        this.props = props;
    }

    @PostMapping("/api/me/push-subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    Subscribed subscribe(Authentication auth, @Valid @RequestBody SubscribeRequest req) {
        return new Subscribed(subscriptions.subscribe(CurrentUser.id(auth), req));
    }

    @DeleteMapping("/api/me/push-subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unsubscribe(Authentication auth, @Valid @RequestBody UnsubscribeRequest req) {
        subscriptions.unsubscribe(CurrentUser.id(auth), req.endpoint());
    }

    @GetMapping("/api/push/vapid-key")
    VapidKey vapidKey() {
        String key = props.push().vapidPublicKey();
        return new VapidKey(key.isBlank() ? null : key);
    }
}
