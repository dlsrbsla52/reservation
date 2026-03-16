package com.media.bus.contract.entity.member;

import com.media.bus.common.entity.common.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Optional;

/**
 * 회원 유형 구분 Enum.
 * - MEMBER : 일반 회원. 예약 생성 및 조회 권한.
 * - BUSINESS: 비즈니스 회원. 예약 가능한 공간/자원 등록 및 관리 권한.
 */
@Getter
@AllArgsConstructor
@SuppressWarnings("unused")
public enum MemberType implements BaseEnum {
    MEMBER("MEMBER", "일반 회원"),
    BUSINESS("BUSINESS", "비즈니스 회원"),
    ADMIN_USER("ADMIN_USER", "관리회원 일반 유저"),
    ADMIN_MASTER("ADMIN_MASTER", "관리회원 마스터(대부분의 권한을 가지고 있음)"),
    ADMIN_DEVELOPER("ADMIN_DEVELOPER", "관리회원 개발자"),
    ;

    private final String name;
    private final String displayName;

    /**
     * Enum 이름으로 검색
     */
    public static Optional<MemberType> fromName(String name) {
        return BaseEnum.fromName(MemberType.class, name);
    }
}
