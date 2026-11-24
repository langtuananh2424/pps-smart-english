package com.pps.smartenglish.auth.service;

import com.pps.smartenglish.auth.domain.Permission;
import com.pps.smartenglish.auth.domain.PermissionOverride;
import com.pps.smartenglish.auth.domain.Role;
import com.pps.smartenglish.auth.dto.RbacDtos.OverrideView;
import com.pps.smartenglish.auth.dto.RbacDtos.PermissionView;
import com.pps.smartenglish.auth.dto.RbacDtos.RoleView;
import com.pps.smartenglish.auth.dto.RbacDtos.StaffView;
import com.pps.smartenglish.auth.dto.RbacDtos.UserAccess;
import com.pps.smartenglish.auth.repository.AccessRepository;
import com.pps.smartenglish.auth.repository.PermissionOverrideRepository;
import com.pps.smartenglish.auth.repository.PermissionRepository;
import com.pps.smartenglish.auth.repository.RoleRepository;
import com.pps.smartenglish.auth.repository.UserRepository;
import com.pps.smartenglish.shared.audit.AuditRecorder;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.security.CurrentUser;
import com.pps.smartenglish.shared.web.CursorPage;
import com.pps.smartenglish.shared.web.Cursors;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quản trị phân quyền (FR-ADM-04): role, quyền của role, gán role, GRANT/REVOKE có hạn. Mỗi thay đổi ghi nhật ký (FR-ADM-05).
 * Không để hệ thống không còn người có quyền rbac.manage: "quyền quản trị cuối cùng" là quyền này.
 */
@Service
public class RbacService {

    static final String RBAC = "rbac.manage";

    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final PermissionOverrideRepository overrides;
    private final AccessRepository access;
    private final UserRepository users;
    private final AuditRecorder audit;
    private final Clock clock;

    RbacService(RoleRepository roles, PermissionRepository permissions, PermissionOverrideRepository overrides,
                AccessRepository access, UserRepository users, AuditRecorder audit, Clock clock) {
        this.roles = roles;
        this.permissions = permissions;
        this.overrides = overrides;
        this.access = access;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PermissionView> permissions() {
        return permissions.findAllByOrderByModuleAscCodeAsc().stream()
                .map(p -> new PermissionView(p.getCode(), p.getModule(), p.getDescription())).toList();
    }

    @Transactional(readOnly = true)
    public List<RoleView> roles() {
        var counts = memberCounts();
        return roles.findAllByOrderByIdAsc().stream().map(r -> roleView(r, counts)).toList();
    }

    /** Nhân sự (có role khác STUDENT), id giảm dần; q khớp một phần email/tên, role lọc theo mã. */
    @Transactional(readOnly = true)
    public CursorPage<StaffView> staff(String q, String role, String limit, String cursor) {
        int size = Cursors.size(limit, 20, 100);
        String key = q == null || q.isBlank() ? null : "%" + escapeLike(q.strip().toLowerCase()) + "%";
        var rows = access.staffPage(Cursors.decode(cursor), role == null || role.isBlank() ? null : role.strip(), key, size + 1);
        boolean more = rows.size() > size;
        var page = more ? rows.subList(0, size) : rows;
        Map<Long, List<String>> rolesOf = new HashMap<>();
        if (!page.isEmpty()) {
            var ids = page.stream().map(r -> ((Number) r[0]).longValue()).toList();
            for (Object[] r : access.roleCodesOfUsers(ids)) {
                rolesOf.computeIfAbsent(((Number) r[0]).longValue(), k -> new ArrayList<>()).add((String) r[1]);
            }
        }
        var data = page.stream().map(r -> {
            long id = ((Number) r[0]).longValue();
            return new StaffView(id, (String) r[1], (String) r[2], rolesOf.getOrDefault(id, List.of()));
        }).toList();
        return new CursorPage<>(data, more ? Cursors.encode(data.get(size - 1).id()) : null);
    }

    private static String escapeLike(String s) {
        return s.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private Map<Long, Long> memberCounts() {
        Map<Long, Long> m = new HashMap<>();
        for (Object[] r : access.roleMemberCounts()) {
            m.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue());
        }
        return m;
    }

    @Transactional
    public RoleView createRole(String code, String name, List<String> permissionCodes) {
        if (roles.existsByCode(code)) {
            throw new ApiException(HttpStatus.CONFLICT, "role_taken", "Mã role đã được dùng");
        }
        var codes = requirePermissions(permissionCodes);
        var role = roles.save(new Role(code, name.strip()));
        if (!codes.isEmpty()) {
            access.addRolePermissions(role.getId(), codes);
        }
        var after = roleView(role);
        audit.record("CREATE", "ROLE", role.getId(), null, snapshot(after));
        return after;
    }

    @Transactional
    public RoleView setRolePermissions(long roleId, List<String> permissionCodes) {
        Role role = roles.findById(roleId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "role_not_found", "Không tìm thấy role"));
        var codes = requirePermissions(permissionCodes);
        var before = roleView(role);
        access.clearRolePermissions(roleId);
        if (!codes.isEmpty()) {
            access.addRolePermissions(roleId, codes);
        }
        requireSomeoneCanManageRbac();
        var after = roleView(role);
        audit.record("UPDATE", "ROLE", roleId, snapshot(before), snapshot(after));
        return after;
    }

    @Transactional
    public UserAccess assignRole(long userId, String roleCode) {
        requireUser(userId);
        requireRole(roleCode);
        access.grantRole(userId, roleCode, actor());
        var after = access(userId);
        audit.record("ASSIGN_ROLE", "USER", userId, null, Set.of(roleCode));
        return after;
    }

    @Transactional
    public UserAccess removeRole(long userId, String roleCode) {
        requireUser(userId);
        requireRole(roleCode);
        access.revokeRole(userId, roleCode);
        requireSomeoneCanManageRbac();
        var after = access(userId);
        audit.record("REMOVE_ROLE", "USER", userId, Set.of(roleCode), null);
        return after;
    }

    /** Thêm hoặc sửa override của một quyền cho một người; hết hạn phải nằm sau thời điểm hiện tại. */
    @Transactional
    public UserAccess setOverride(long userId, String permissionCode, String effect, Instant expiresAt) {
        requireUser(userId);
        var perm = requirePermission(permissionCode);
        Instant now = Instant.now(clock);
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw ApiException.validation("expiresAt", "Hạn phải sau thời điểm hiện tại");
        }
        var existing = overrides.findByUserIdAndPermissionId(userId, perm.getId());
        var before = existing.map(this::overrideView).orElse(null);
        long actor = actor();
        if (existing.isPresent()) {
            existing.get().change(effect, expiresAt, actor);
        } else {
            overrides.save(new PermissionOverride(userId, perm.getId(), effect, expiresAt, actor));
        }
        overrides.flush();
        requireSomeoneCanManageRbac();
        var after = access(userId);
        audit.record("SET_OVERRIDE", "USER", userId, before, new OverrideView(permissionCode, effect, expiresAt, actor));
        return after;
    }

    @Transactional
    public UserAccess removeOverride(long userId, String permissionCode) {
        requireUser(userId);
        var perm = requirePermission(permissionCode);
        var existing = overrides.findByUserIdAndPermissionId(userId, perm.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "override_not_found", "Người này không có override cho quyền đó"));
        var before = overrideView(existing);
        overrides.delete(existing);
        overrides.flush();
        requireSomeoneCanManageRbac();
        var after = access(userId);
        audit.record("REMOVE_OVERRIDE", "USER", userId, before, null);
        return after;
    }

    @Transactional(readOnly = true)
    public UserAccess access(long userId) {
        var overrideViews = overrides.findByUserIdOrderByIdAsc(userId).stream().map(this::overrideView).toList();
        return new UserAccess(userId, access.roleCodesOf(userId), overrideViews,
                access.effectivePermissions(userId, Instant.now(clock)).stream().sorted().toList());
    }

    /** Snapshot nhật ký không có memberCount (số liệu đổi theo gán role, không phải thay đổi của role). */
    private static Map<String, Object> snapshot(RoleView v) {
        return Map.of("id", v.id(), "code", v.code(), "name", v.name(), "permissions", v.permissions());
    }

    private RoleView roleView(Role role) {
        return roleView(role, memberCounts());
    }

    private RoleView roleView(Role role, Map<Long, Long> counts) {
        return new RoleView(role.getId(), role.getCode(), role.getName(),
                access.permissionCodesOfRole(role.getId()).stream().sorted().toList(), counts.getOrDefault(role.getId(), 0L));
    }

    private OverrideView overrideView(PermissionOverride o) {
        String code = permissions.findById(o.getPermissionId()).map(Permission::getCode).orElse(null);
        return new OverrideView(code, o.getEffect(), o.getExpiresAt(), o.getGrantedBy());
    }

    /** Kiểm tra mã quyền tồn tại; trả danh sách mã đã khử trùng. */
    private List<String> requirePermissions(List<String> codes) {
        var distinct = codes.stream().distinct().toList();
        if (distinct.isEmpty()) {
            return distinct;
        }
        var found = permissions.findByCodeIn(distinct).stream().map(Permission::getCode).collect(Collectors.toSet());
        var missing = distinct.stream().filter(c -> !found.contains(c)).toList();
        if (!missing.isEmpty()) {
            throw ApiException.validation("permissions", "Không có quyền: " + String.join(", ", missing));
        }
        return distinct;
    }

    private Permission requirePermission(String code) {
        return permissions.findByCode(code)
                .orElseThrow(() -> ApiException.validation("permissionCode", "Không có quyền: " + code));
    }

    private void requireRole(String code) {
        if (!roles.existsByCode(code)) {
            throw ApiException.validation("roleCode", "Không có role: " + code);
        }
    }

    private void requireUser(long userId) {
        if (!users.existsById(userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "user_not_found", "Không tìm thấy tài khoản");
        }
    }

    /** Chặn thay đổi làm không còn ai có quyền rbac.manage (rollback cùng transaction). */
    private void requireSomeoneCanManageRbac() {
        access.lockAdminRole(); // vì sao: hai transaction đồng thời không được cùng thấy "còn một người"
        if (access.countUsersWithPermission(RBAC, Instant.now(clock)).longValue() == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "last_admin",
                    "Phải còn ít nhất một người có quyền quản lý phân quyền");
        }
    }

    private static long actor() {
        return CurrentUser.id(SecurityContextHolder.getContext().getAuthentication());
    }
}
