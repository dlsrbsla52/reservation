package com.media.bus.auth.permission.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 역할-권한 매핑 JPA Entity.
 * auth.role_permission 테이블과 매핑됩니다.
 *
 * 역할(MemberType.name())과 권한(Permission.name())의 M:N 관계를 저장합니다.
 * DB가 권한의 단일 source of truth — 코드 재배포 없이 운영 중 권한 변경이 가능합니다.
 *
 * 생성은 정적 팩토리 메서드 of()를 통해서만 허용합니다.
 */
@Entity
@Table(name = "role_permission", schema = "auth")
@IdClass(RolePermissionId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RolePermission {

    @Id
    @Column(name = "role_name", nullable = false, length = 30)
    private String roleName;

    @Id
    @Column(name = "permission_name", nullable = false, length = 20)
    private String permissionName;

    /**
     * 정적 팩토리 메서드.
     *
     * @param roleName       MemberType.name() 값 (예: "ADMIN_USER")
     * @param permissionName Permission.name() 값 (예: "WRITE")
     */
    public static RolePermission of(String roleName, String permissionName) {
        RolePermission rp = new RolePermission();
        rp.roleName = roleName;
        rp.permissionName = permissionName;
        return rp;
    }

    public String getRoleName() {
        return roleName;
    }

    public String getPermissionName() {
        return permissionName;
    }
}
