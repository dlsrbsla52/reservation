package com.media.bus.auth.modules.auth.entity;

import com.media.bus.auth.modules.member.entity.Member;
import com.media.bus.common.entity.common.DateBaseEntity;
import jakarta.persistence.Column;
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
 * 회원(Member)과 역할(MemberType.name())의 관계를 저장합니다.
 * member.member_type 컬럼을 대체하여 역할 정보를 별도 테이블로 분리합니다.
 * 향후 한 회원이 복수의 역할을 가질 수 있는 구조를 지원합니다.
 *
 * UUID PK는 BaseEntity에서 상속. (member_id, role_name) 조합은 UNIQUE 제약으로 보장.
 * 생성은 정적 팩토리 메서드 of()를 통해서만 허용합니다.
 */
@Getter
@Entity
@Table(
    name = "member_role",
    schema = "auth",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_member_role",
        columnNames = {"member_id", "role_name"}
    )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberRole extends DateBaseEntity {

    /**
     * 역할을 소유한 회원. LAZY 로딩으로 불필요한 JOIN을 방지합니다.
     * member_id FK 컬럼으로 매핑되며, 생성 후 변경 불가(updatable = false).
     * -- GETTER --
     *  연관된 회원의 PK를 반환합니다.
     *  member 프록시를 초기화하지 않고 FK 값만 필요한 경우 사용하십시오.

     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, updatable = false)
    private Member member;

    @Column(name = "role_name", nullable = false, length = 30)
    private String roleName;

    /**
     * 정적 팩토리 메서드.
     *
     * @param member  역할을 부여할 회원 엔티티
     * @param roleName MemberType.name() 값 (예: "MEMBER", "ADMIN_USER")
     */
    public static MemberRole of(Member member, String roleName) {
        MemberRole mr = new MemberRole();
        mr.member = member;
        mr.roleName = roleName;
        return mr;
    }

}
