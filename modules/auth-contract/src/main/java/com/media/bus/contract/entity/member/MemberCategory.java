package com.media.bus.contract.entity.member;

import com.media.bus.common.entity.common.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 회원 유형을 큰 카테고리로 분류하는 Enum.
 * MemberType마다 하나의 카테고리를 가지며,
 * @Authorize.categories() 조건 매칭에 사용됩니다.
 *
 * 예) ADMIN 카테고리 전체를 허용하면 ADMIN_USER / ADMIN_MASTER / ADMIN_DEVELOPER 모두 통과.
 */
@Getter
@AllArgsConstructor
@SuppressWarnings("unused")
public enum MemberCategory implements BaseEnum {
    USER("USER", "일반 사용자"),
    BUSINESS("BUSINESS", "비즈니스"),
    ADMIN("ADMIN", "관리자"),
    ;

    private final String name;
    private final String displayName;
}
