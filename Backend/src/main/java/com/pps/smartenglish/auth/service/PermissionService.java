package com.pps.smartenglish.auth.service;

import com.pps.smartenglish.auth.repository.AccessRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Kiểm tra quyền hiệu lực của một người (FR-ADM-04), đọc từ DB mỗi lần. */
@Service
public class PermissionService {

    /** Thứ tự ưu tiên khi cần một role duy nhất để hiển thị (trường role trong response). */
    static final List<String> DISPLAY_ORDER = List.of("ADMIN", "EDITOR", "DIRECTOR", "ACCOUNTANT", "SUPPORT", "TEACHER",
            "STUDENT");

    private final AccessRepository access;
    private final Clock clock;

    PermissionService(AccessRepository access, Clock clock) {
        this.access = access;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean has(long userId, String permission) {
        return effective(userId).contains(permission);
    }

    @Transactional(readOnly = true)
    public Set<String> effective(long userId) {
        return Set.copyOf(access.effectivePermissions(userId, Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    public List<String> roleCodes(long userId) {
        return access.roleCodesOf(userId);
    }

    /** Role ưu tiên cao nhất của người này; không có role nào thì STUDENT. */
    @Transactional(readOnly = true)
    public String primaryRole(long userId) {
        var codes = roleCodes(userId);
        return DISPLAY_ORDER.stream().filter(codes::contains).findFirst().orElse("STUDENT");
    }
}
