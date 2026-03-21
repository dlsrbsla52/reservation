package com.media.bus.auth.modules.auth.repository;

import com.media.bus.auth.modules.auth.entity.MemberRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;
import java.util.UUID;

/**
 * 회원-역할 매핑 Repository.
 * 로그인 및 토큰 재발급 시 해당 회원의 역할 이름 집합을 조회합니다.
 * member.member_type을 대체하는 별도 테이블 기반 역할 조회를 제공합니다.
 */
public interface MemberRoleRepository extends JpaRepository<MemberRole, UUID> {

    /**
     * 회원 ID로 해당 회원에 매핑된 역할 이름 집합을 조회합니다.
     * AuthService의 로그인/토큰 갱신 및 MemberService의 회원 정보 조회 시 사용됩니다.
     *
     * @param memberId 회원 PK
     * @return 역할 이름 집합 (예: ["MEMBER"], ["ADMIN_USER"])
     */
    @Query("SELECT mr.roleName FROM MemberRole mr WHERE mr.member.id = :memberId")
    Set<String> findRoleNamesByMemberId(@Param("memberId") UUID memberId);
}
