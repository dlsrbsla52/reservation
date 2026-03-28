package com.media.bus.auth.modules.auth.entity;

import com.media.bus.common.entity.common.DateBaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 역할-권한 매핑 JPA Entity.
 * auth.role_permission 테이블과 매핑됩니다.
 *
 * 역할(Role)과 권한(Permission)의 관계를 저장합니다.
 * DB가 권한의 단일 source of truth — 코드 재배포 없이 운영 중 권한 변경이 가능합니다.
 * (role_id, permission_id) 조합은 UNIQUE 제약으로 유일성을 보장합니다.
 * 생성은 정적 팩토리 메서드 of()를 통해서만 허용합니다.
 */
@Getter
@Entity
@Table(
    name = "role_permission",
    schema = "auth",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_role_permission",
        columnNames = {"role_id", "permission_id"}
    )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RolePermission extends DateBaseEntity {

    /** 권한이 부여된 역할. LAZY 로딩. 생성 후 변경 가능. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    /** 부여된 권한. LAZY 로딩. 생성 후 변경 가능. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_id", nullable = false)
    private Permission permission;

    public static RolePermission of(Role role, Permission permission) {
        RolePermission rp = new RolePermission();
        rp.role = role;
        rp.permission = permission;
        return rp;
    }
}
