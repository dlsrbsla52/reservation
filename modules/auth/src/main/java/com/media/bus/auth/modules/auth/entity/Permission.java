package com.media.bus.auth.modules.auth.entity;

import com.media.bus.common.entity.common.DateBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 권한 마스터 JPA Entity.
 * auth.permission 테이블과 매핑됩니다.
 *
 * UUID PK는 DateBaseEntity(BaseEntity)가 관리합니다.
 * name은 UNIQUE 제약이 있는 비즈니스 식별자입니다.
 * 생성은 정적 팩토리 메서드 of()를 통해서만 허용합니다.
 */
@Getter
@Entity
@Table(name = "permission", schema = "auth")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Permission extends DateBaseEntity {

    /** 권한 식별자 (비즈니스 키). 예: "READ", "WRITE", "DELETE", "MANAGE" */
    @Column(name = "name", nullable = false, unique = true, length = 20)
    private String name;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    public static Permission of(String name, String displayName) {
        Permission permission = new Permission();
        permission.name = name;
        permission.displayName = displayName;
        return permission;
    }
}
