package com.media.bus.auth.modules.auth.entity;

import com.media.bus.common.entity.common.DateBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 역할 마스터 JPA Entity.
 * auth.role 테이블과 매핑됩니다.
 *
 * MemberType enum의 name() 값을 DB 레코드로 관리합니다.
 * member_role, role_permission 두 테이블이 이 엔티티의 PK(UUID)를 FK로 참조합니다.
 * 생성은 정적 팩토리 메서드 of()를 통해서만 허용합니다.
 */
@Getter
@Entity
@Table(name = "role", schema = "auth")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Role extends DateBaseEntity {

    /** MemberType.name() 값 (예: "MEMBER", "ADMIN_USER"). UNIQUE 제약 보장. */
    @Column(name = "name", nullable = false, unique = true, length = 30)
    private String name;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    public static Role of(String name, String displayName) {
        Role role = new Role();
        role.name = name;
        role.displayName = displayName;
        return role;
    }
}
