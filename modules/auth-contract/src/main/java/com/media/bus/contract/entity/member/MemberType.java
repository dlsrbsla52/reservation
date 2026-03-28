package com.media.bus.contract.entity.member;

import com.media.bus.common.entity.common.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Optional;

/// 회원 유형 구분 Enum.
/// 각 타입은 소속 카테고리(MemberCategory)를 가집니다.
/// 권한 정보는 DB(auth.role\_permission)에서 관리되며,
/// 로그인/토큰 재발급 시 조회하여 JWT claim에 포함됩니다.
/// 하위 서비스는 X-User-Permissions 헤더로 권한 Set을 복원합니다.
/// - MEMBER          : 일반 회원
/// - BUSINESS        : 비즈니스 회원
/// - ADMIN\_USER      : 관리회원 일반
/// - ADMIN\_MASTER    : 관리회원 마스터
/// - ADMIN\_DEVELOPER : 관리회원 개발자
@Getter
@AllArgsConstructor
@SuppressWarnings("unused")
public enum MemberType implements BaseEnum {
    MEMBER("MEMBER", "일반 회원", MemberCategory.USER),
    BUSINESS("BUSINESS", "비즈니스 회원", MemberCategory.BUSINESS),
    ADMIN_USER("ADMIN_USER", "관리회원 일반 유저", MemberCategory.ADMIN),
    ADMIN_MASTER("ADMIN_MASTER", "관리회원 마스터(대부분의 권한을 가지고 있음)", MemberCategory.ADMIN),
    ADMIN_DEVELOPER("ADMIN_DEVELOPER", "관리회원 개발자", MemberCategory.ADMIN),
    ;

    private final String name;
    private final String displayName;
    private final MemberCategory category;

    /// ADMIN 카테고리 여부
    public boolean isAdmin() {
        return this.category == MemberCategory.ADMIN;
    }

    /// USER 카테고리 여부
    public boolean isUser() {
        return this.category == MemberCategory.USER;
    }

    /// BUSINESS 카테고리 여부
    public boolean isBusiness() {
        return this.category == MemberCategory.BUSINESS;
    }

    /// Enum name으로 검색
    public static Optional<MemberType> fromName(String name) {
        return BaseEnum.fromName(MemberType.class, name);
    }
}
