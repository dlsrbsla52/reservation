package com.media.bus.auth.modules.auth.repository;

import com.media.bus.auth.modules.auth.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;
import java.util.UUID;

/**
 * 역할-권한 매핑 Repository.
 * 로그인 및 토큰 재발급 시 해당 역할의 권한 이름 집합을 조회합니다.
 * DB를 권한의 단일 source of truth로 사용하므로, 운영 중 권한 변경이 즉시 반영됩니다.
 * (단, 기존 발급된 Access Token의 TTL 내에서는 구권한이 유지됩니다.)
 */
public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {

    /**
     * 역할 이름으로 해당 역할에 매핑된 권한 이름 집합을 조회합니다.
     * AuthService의 로그인/토큰 갱신 시 1회 호출됩니다.
     *
     * @param roleName MemberType.name() 값 (예: "ADMIN_USER")
     * @return 권한 이름 집합 (예: ["READ", "WRITE"])
     */
    @Query("SELECT rp.permission.name FROM RolePermission rp WHERE rp.role.name = :roleName")
    Set<String> findPermissionNamesByRoleName(@Param("roleName") String roleName);
}
