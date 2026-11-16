package com.pps.smartenglish.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** DTO phân quyền (FR-ADM-04): yêu cầu và phản hồi của màn bảng chọn quyền. */
public final class RbacDtos {

    private RbacDtos() {}

    // ---- yêu cầu ----

    public record CreateRole(@NotBlank @Pattern(regexp = "[A-Z][A-Z_]{1,49}", message = "Mã role dạng IN_HOA_GACH_DUOI") String code,
                             @NotBlank @Size(max = 100) String name,
                             @NotNull List<String> permissions) {}

    public record SetRolePermissions(@NotNull List<String> permissions) {}

    public record AssignRole(@NotBlank String roleCode) {}

    /** effect: GRANT hoặc REVOKE. expiresAt null = không hết hạn; nếu có thì phải sau thời điểm hiện tại. */
    public record SetOverride(@NotBlank @Pattern(regexp = "GRANT|REVOKE", message = "Phải là GRANT hoặc REVOKE") String effect,
                              Instant expiresAt) {}

    // ---- phản hồi ----

    public record PermissionView(String code, String module, String description) {}

    public record RoleView(Long id, String code, String name, List<String> permissions) {}

    public record OverrideView(String permission, String effect, Instant expiresAt, Long grantedBy) {}

    /** effective = hợp quyền các role − REVOKE còn hạn + GRANT còn hạn. */
    public record UserAccess(Long userId, List<String> roles, List<OverrideView> overrides, List<String> effective) {}
}
