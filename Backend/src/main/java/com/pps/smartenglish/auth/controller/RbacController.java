package com.pps.smartenglish.auth.controller;

import com.pps.smartenglish.auth.dto.RbacDtos.AssignRole;
import com.pps.smartenglish.auth.dto.RbacDtos.CreateRole;
import com.pps.smartenglish.auth.dto.RbacDtos.MyPermissions;
import com.pps.smartenglish.auth.dto.RbacDtos.PermissionView;
import com.pps.smartenglish.auth.dto.RbacDtos.RoleView;
import com.pps.smartenglish.auth.dto.RbacDtos.SetOverride;
import com.pps.smartenglish.auth.dto.RbacDtos.SetRolePermissions;
import com.pps.smartenglish.auth.dto.RbacDtos.StaffView;
import com.pps.smartenglish.auth.dto.RbacDtos.UserAccess;
import com.pps.smartenglish.auth.service.PermissionService;
import com.pps.smartenglish.auth.service.RbacService;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.security.CurrentUser;
import com.pps.smartenglish.shared.web.CursorPage;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Bảng chọn quyền và gán role (FR-ADM-04). Cần quyền rbac.manage, khai báo trong SecurityConfig. */
@RestController
class RbacController {

    private final RbacService rbac;
    private final PermissionService permissionService;

    RbacController(RbacService rbac, PermissionService permissionService) {
        this.rbac = rbac;
        this.permissionService = permissionService;
    }

    @GetMapping("/api/admin/permissions")
    List<PermissionView> permissions() {
        return rbac.permissions();
    }

    /** Quyền hiệu lực của chính người đăng nhập, để web dựng menu. */
    @GetMapping("/api/me/permissions")
    MyPermissions myPermissions(Authentication auth) {
        return new MyPermissions(permissionService.effective(CurrentUser.id(auth)).stream().sorted().toList());
    }

    @GetMapping("/api/admin/users")
    CursorPage<StaffView> staff(@RequestParam(required = false) String q, @RequestParam(required = false) String role,
                                @RequestParam(required = false) String limit, @RequestParam(required = false) String cursor) {
        return rbac.staff(q, role, limit, cursor);
    }

    @GetMapping("/api/admin/roles")
    List<RoleView> roles() {
        return rbac.roles();
    }

    @PostMapping("/api/admin/roles")
    @ResponseStatus(HttpStatus.CREATED)
    RoleView createRole(@Valid @RequestBody CreateRole req) {
        return rbac.createRole(req.code(), req.name(), req.permissions());
    }

    @PutMapping("/api/admin/roles/{id}/permissions")
    RoleView setRolePermissions(@PathVariable String id, @Valid @RequestBody SetRolePermissions req) {
        return rbac.setRolePermissions(roleId(id), req.permissions());
    }

    @GetMapping("/api/admin/users/{id}/access")
    UserAccess access(@PathVariable String id) {
        return rbac.access(userId(id));
    }

    @PostMapping("/api/admin/users/{id}/roles")
    UserAccess assignRole(@PathVariable String id, @Valid @RequestBody AssignRole req) {
        return rbac.assignRole(userId(id), req.roleCode());
    }

    @DeleteMapping("/api/admin/users/{id}/roles/{code}")
    UserAccess removeRole(@PathVariable String id, @PathVariable String code) {
        return rbac.removeRole(userId(id), code);
    }

    @PutMapping("/api/admin/users/{id}/permissions/{code}")
    UserAccess setOverride(@PathVariable String id, @PathVariable String code, @Valid @RequestBody SetOverride req) {
        return rbac.setOverride(userId(id), code, req.effect(), req.expiresAt());
    }

    @DeleteMapping("/api/admin/users/{id}/permissions/{code}")
    UserAccess removeOverride(@PathVariable String id, @PathVariable String code) {
        return rbac.removeOverride(userId(id), code);
    }

    private static long roleId(String raw) {
        return numericId(raw, "role_not_found", "Không tìm thấy role");
    }

    private static long userId(String raw) {
        return numericId(raw, "user_not_found", "Không tìm thấy tài khoản");
    }

    private static long numericId(String raw, String code, String message) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, code, message);
        }
    }
}
