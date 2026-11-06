package com.pps.smartenglish.catalog.service;

import com.pps.smartenglish.shared.error.ApiException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * lessons.video_ref của bài YouTube lưu mã video 11 ký tự (client nhúng bằng mã đó, LessonService trả nguyên giá trị).
 * CMS nhận mã hoặc URL YouTube thông dụng và luôn chuẩn hóa về mã trước khi lưu.
 */
final class YoutubeRef {

    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Set<String> HOSTS = Set.of("youtube.com", "www.youtube.com", "m.youtube.com");
    private static final Set<String> PATH_PREFIXES = Set.of("embed", "shorts", "live");

    private YoutubeRef() {}

    static String normalize(String raw) {
        String s = raw == null ? "" : raw.strip();
        String id = ID.matcher(s).matches() ? s : fromUrl(s);
        if (id == null || !ID.matcher(id).matches()) {
            throw ApiException.validation("videoRef", "Phải là mã video YouTube 11 ký tự hoặc URL YouTube hợp lệ");
        }
        return id;
    }

    private static String fromUrl(String s) {
        try {
            URI u = new URI(s);
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
            String path = u.getPath() == null ? "" : u.getPath();
            if (!"https".equalsIgnoreCase(u.getScheme()) && !"http".equalsIgnoreCase(u.getScheme())) {
                return null;
            }
            if ("youtu.be".equals(host)) {
                return path.length() > 1 ? path.substring(1) : null;
            }
            if (!HOSTS.contains(host)) {
                return null;
            }
            if ("/watch".equals(path) && u.getQuery() != null) {
                for (String pair : u.getQuery().split("&")) {
                    if (pair.startsWith("v=")) {
                        return pair.substring(2);
                    }
                }
                return null;
            }
            String[] parts = path.split("/");
            return parts.length == 3 && PATH_PREFIXES.contains(parts[1]) ? parts[2] : null;
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
