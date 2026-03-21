package com.media.bus.auth.modules.member.repository;

import com.media.bus.auth.modules.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * 회원 JPA Repository.
 * 인증 목적의 조회 메서드를 정의합니다.
 */
public interface MemberRepository extends JpaRepository<Member, UUID> {

    /**
     * 로그인 아이디로 회원 조회.
     * 로그인 및 중복 검사에 사용합니다.
     */
    Optional<Member> findByLoginId(String loginId);

    /**
     * 이메일로 회원 조회.
     * 이메일 중복 검사 및 이메일 인증 처리에 사용합니다.
     */
    Optional<Member> findByEmail(String email);

    /**
     * 로그인 아이디 존재 여부 확인.
     * 회원가입 시 중복 아이디 검증에 사용합니다.
     */
    boolean existsByLoginId(String loginId);

    /**
     * 이메일 존재 여부 확인.
     * 회원가입 시 중복 이메일 검증에 사용합니다.
     */
    boolean existsByEmail(String email);
}
