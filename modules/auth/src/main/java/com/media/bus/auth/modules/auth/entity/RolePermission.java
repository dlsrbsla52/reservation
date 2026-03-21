package com.media.bus.auth.modules.auth.entity;

import com.media.bus.common.entity.common.DateBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 역할-권한 매핑 JPA Entity.
 * auth.role_permission 테이블과 매핑됩니다.
 *<p>
 * 역할(MemberType.name())과 권한(Permission.name())의 M:N 관계를 저장합니다.
 * DB가 권한의 단일 source of truth — 코드 재배포 없이 운영 중 권한 변경이 가능합니다.
 *</p>
 * UUID PK는 BaseEntity에서 상속. (role_name, permission_name) 조합은 UNIQUE 제약으로 보장.
 * 생성은 정적 팩토리 메서드 of()를 통해서만 허용합니다.
 */
@Getter
@Entity
@Table(
    name = "role_permission",
    schema = "auth",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_role_permission",
        columnNames = {"role_name", "permission_name"}
    )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RolePermission extends DateBaseEntity {

    @Column(name = "role_name", nullable = false, length = 30)
    private String roleName;

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

}
