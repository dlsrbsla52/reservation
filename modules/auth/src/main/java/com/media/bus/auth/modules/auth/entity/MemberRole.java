package com.media.bus.auth.modules.auth.entity;

import com.media.bus.auth.modules.member.entity.Member;
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
 * 회원-역할 매핑 JPA Entity.
 * auth.member_role 테이블과 매핑됩니다.
 *
 * 회원(Member)과 역할(Role)의 관계를 저장합니다.
 * member_id는 UNIQUE 제약으로 한 회원이 하나의 역할만 가질 수 있도록 보장합니다.
 * 생성은 정적 팩토리 메서드 of()를 통해서만 허용합니다.
 */
@Getter
@Entity
@Table(
    name = "member_role",
    schema = "auth",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_member_role_single",
        columnNames = {"member_id"}
    )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberRole extends DateBaseEntity {

    /** 역할을 소유한 회원. LAZY 로딩으로 불필요한 JOIN 방지. 생성 후 변경 불가. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, updatable = false)
    private Member member;

    /** 부여된 역할. LAZY 로딩. 생성 후 변경 불가. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false, updatable = false)
    private Role role;

    public static MemberRole of(Member member, Role role) {
        MemberRole mr = new MemberRole();
        mr.member = member;
        mr.role = role;
        return mr;
    }
}
