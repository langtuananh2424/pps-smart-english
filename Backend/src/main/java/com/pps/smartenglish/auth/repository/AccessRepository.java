package com.pps.smartenglish.auth.repository;

import com.pps.smartenglish.auth.domain.User;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Truy vấn phân quyền (FR-ADM-04). Quyền hiệu lực = hợp quyền các role − REVOKE còn hạn + GRANT còn hạn. Luôn đọc từ DB để
 * thay đổi có hiệu lực ở lần gọi kế tiếp.
 */
public interface AccessRepository extends JpaRepository<User, Long> {

    @Query(value = """
            select p.code from permissions p
            where p.id in (select rp.permission_id from role_permissions rp
                           join user_roles ur on ur.role_id = rp.role_id where ur.user_id = :userId)
              and not exists (select 1 from user_permission_overrides o
                              where o.user_id = :userId and o.permission_id = p.id and o.effect = 'REVOKE'
                                and (o.expires_at is null or o.expires_at > :now))
            union
            select p.code from user_permission_overrides o join permissions p on p.id = o.permission_id
            where o.user_id = :userId and o.effect = 'GRANT' and (o.expires_at is null or o.expires_at > :now)
            """, nativeQuery = true)
    List<String> effectivePermissions(@Param("userId") long userId, @Param("now") Instant now);

    /** Số tài khoản ACTIVE có quyền từ nguồn bền (role, hoặc GRANT không hết hạn), không bị REVOKE còn hiệu lực. GRANT có hạn không tính: hết hạn là mất người quản trị. */
    @Query(value = """
            select count(*) from users u
            where u.status = 'ACTIVE' and (
              (exists (select 1 from user_roles ur join role_permissions rp on rp.role_id = ur.role_id
                         join permissions p on p.id = rp.permission_id
                       where ur.user_id = u.id and p.code = :code)
               and not exists (select 1 from user_permission_overrides o join permissions p on p.id = o.permission_id
                               where o.user_id = u.id and p.code = :code and o.effect = 'REVOKE'
                                 and (o.expires_at is null or o.expires_at > :now)))
              or exists (select 1 from user_permission_overrides o join permissions p on p.id = o.permission_id
                         where o.user_id = u.id and p.code = :code and o.effect = 'GRANT' and o.expires_at is null))
            """, nativeQuery = true)
    Number countUsersWithPermission(@Param("code") String code, @Param("now") Instant now);

    /** Khóa dòng role ADMIN để các thay đổi RBAC chạy tuần tự (chống write-skew khi hai admin gỡ nhau). */
    @Query(value = "select id from roles where code = 'ADMIN' for update", nativeQuery = true)
    List<Long> lockAdminRole();

    @Query(value = "select r.code from roles r join user_roles ur on ur.role_id = r.id where ur.user_id = :userId", nativeQuery = true)
    List<String> roleCodesOf(@Param("userId") long userId);

    @Query(value = "select p.code from permissions p join role_permissions rp on rp.permission_id = p.id where rp.role_id = :roleId",
            nativeQuery = true)
    List<String> permissionCodesOfRole(@Param("roleId") long roleId);

    /** [role_id, số user] của mọi role có người; một query cho cả danh sách. */
    @Query(value = "select role_id, count(*) from user_roles group by role_id", nativeQuery = true)
    List<Object[]> roleMemberCounts();

    /**
     * [id, email, tên hiển thị] của nhân sự (có ít nhất một role khác STUDENT), id giảm dần, id < before.
     * role/like null thì bỏ điều kiện; like đã escape bằng '!'.
     */
    @Query(value = """
            select u.id, u.email, p.display_name from users u left join user_profiles p on p.user_id = u.id
            where u.id < :before
              and exists (select 1 from user_roles ur join roles r on r.id = ur.role_id
                          where ur.user_id = u.id and r.code <> 'STUDENT' and (cast(:role as varchar) is null or r.code = :role))
              and (cast(:like as varchar) is null or lower(u.email) like :like escape '!' or lower(p.display_name) like :like escape '!')
            order by u.id desc limit :max
            """, nativeQuery = true)
    List<Object[]> staffPage(@Param("before") long before, @Param("role") String role, @Param("like") String like,
                             @Param("max") int max);

    /** [user_id, mã role] của các user cho trước, một query. */
    @Query(value = "select ur.user_id, r.code from user_roles ur join roles r on r.id = ur.role_id where ur.user_id in (:ids) order by r.id",
            nativeQuery = true)
    List<Object[]> roleCodesOfUsers(@Param("ids") Collection<Long> ids);

    /** Gán role theo mã; đã có thì bỏ qua. Trả số dòng thêm mới. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into user_roles (user_id, role_id, granted_by)
            select :userId, r.id, :grantedBy from roles r
            where r.code = :code and not exists (select 1 from user_roles ur where ur.user_id = :userId and ur.role_id = r.id)
            """, nativeQuery = true)
    int grantRole(@Param("userId") long userId, @Param("code") String code, @Param("grantedBy") Long grantedBy);

    @Modifying(flushAutomatically = true)
    @Query(value = "delete from user_roles where user_id = :userId and role_id = (select id from roles where code = :code)",
            nativeQuery = true)
    int revokeRole(@Param("userId") long userId, @Param("code") String code);

    @Modifying(flushAutomatically = true)
    @Query(value = "delete from role_permissions where role_id = :roleId", nativeQuery = true)
    int clearRolePermissions(@Param("roleId") long roleId);

    @Modifying(flushAutomatically = true)
    @Query(value = "insert into role_permissions (role_id, permission_id) select :roleId, id from permissions where code in (:codes)",
            nativeQuery = true)
    int addRolePermissions(@Param("roleId") long roleId, @Param("codes") Collection<String> codes);
}
