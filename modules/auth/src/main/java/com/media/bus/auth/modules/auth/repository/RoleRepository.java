package com.media.bus.auth.modules.auth.repository;

import com.media.bus.auth.modules.auth.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * 역할 마스터 Repository.
 * 회원가입 시 MemberType.name()으로 Role 엔티티를 조회하는 데 사용됩니다.
 */
public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByName(String name);
}
