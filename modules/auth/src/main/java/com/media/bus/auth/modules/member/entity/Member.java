package com.media.bus.auth.modules.member.entity;

import com.media.bus.auth.modules.auth.entity.MemberRole;
import com.media.bus.auth.modules.member.entity.enumerated.MemberStatus;
import com.media.bus.common.entity.common.DateBaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * 회원 인증 정보 JPA Entity.
 * auth 스키마의 member 테이블과 매핑됩니다.
 * 인증/인가에 필요한 최소 정보만 관리합니다.
 */
@Entity
@SuppressWarnings("unused")
@Table(name = "member", schema = "auth", uniqueConstraints = {
        @UniqueConstraint(name = "uq_member_login_id", columnNames = "login_id"),
        @UniqueConstraint(name = "uq_member_email", columnNames = "email")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Member extends DateBaseEntity {

    @Column(name = "login_id", nullable = false, length = 50)
    private String loginId;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "email", nullable = false, length = 100)
    private String email;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private MemberStatus status = MemberStatus.ACTIVE;

    /**
     * 비즈니스 회원 전용. 일반 회원(MEMBER)의 경우 null.
     */
    @Column(name = "business_number", length = 20)
    private String businessNumber;

    /**
     * 회원에게 부여된 역할 목록.
     * MemberRole 엔티티의 member 필드와 양방향 매핑됩니다.
     * 회원 삭제 시 연관된 역할도 함께 삭제(orphanRemoval)됩니다.
     */
    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<MemberRole> roles = new ArrayList<>();

    // =========================================================
    // 도메인 행위 메서드 (Business Methods)
    // =========================================================

    public void verifyEmail() {
        this.emailVerified = true;
    }

    public void withdraw() {
        this.status = MemberStatus.WITHDRAWN;
    }

    public void suspend() {
        this.status = MemberStatus.SUSPENDED;
    }
}
