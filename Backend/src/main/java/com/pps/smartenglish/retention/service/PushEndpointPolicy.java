package com.pps.smartenglish.retention.service;

import com.pps.smartenglish.retention.config.RetentionProperties;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Server sẽ POST vào endpoint do client gửi, nên chỉ nhận https tới host của dịch vụ push thật (chặn SSRF).
 * Mục cấu hình bắt đầu bằng "." là hậu tố ("a.push.apple.com" hợp lệ, "push.apple.com.evil.com" thì không).
 */
@Component
class PushEndpointPolicy {

    private final List<String> allowedHosts;

    PushEndpointPolicy(RetentionProperties props) {
        this.allowedHosts = props.push().allowedHosts() == null ? List.of() : props.push().allowedHosts();
    }

    boolean allows(String endpoint) {
        URI uri;
        try {
            uri = new URI(endpoint);
        } catch (URISyntaxException e) {
            return false;
        }
        // không userinfo ("https://fcm.googleapis.com@evil.com"), chỉ cổng 443
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || uri.getHost() == null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return allowedHosts.stream().anyMatch(h -> h.startsWith(".")
                ? host.endsWith(h) && host.length() > h.length()
                : host.equals(h));
    }
}
