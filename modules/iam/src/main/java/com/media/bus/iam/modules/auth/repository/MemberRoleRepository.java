package com.media.bus.iam.modules.auth.repository;

import com.media.bus.iam.modules.auth.entity.MemberRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 회원-역할 매핑 Repository.
 * 로그인 및 토큰 재발급 시 해당 회원의 역할 정보를 조회합니다.
 * member.member_type을 대체하는 별도 테이블 기반 역할 조회를 제공합니다.
 */
public interface MemberRoleRepository extends JpaRepository<MemberRole, UUID> {

    /**
     * 회원 ID로 역할 이름 집합을 조회합니다 (Role 엔티티 JOIN 없이 이름만 반환).
     * Guard/Validator 계층 등 이름만 필요한 경우에 사용합니다.
     *
     * @param memberId 회원 PK
     * @return 역할 이름 집합
     */
    @Query("SELECT mr.role.name FROM MemberRole mr WHERE mr.member.id = :memberId")
    Set<String> findRoleNamesByMemberId(@Param("memberId") UUID memberId);

    /**
     * 회원 ID로 MemberRole 목록을 Role까지 JOIN FETCH하여 조회합니다.
     * N+1 쿼리 방지를 위해 로그인/토큰 갱신/회원 정보 조회 시 사용합니다.
     *
     * @param memberId 회원 PK
     * @return MemberRole 목록 (Role 엔티티 포함)
     */
    @Query("SELECT mr FROM MemberRole mr JOIN FETCH mr.role WHERE mr.member.id = :memberId")
    List<MemberRole> findWithRoleByMemberId(@Param("memberId") UUID memberId);
}
