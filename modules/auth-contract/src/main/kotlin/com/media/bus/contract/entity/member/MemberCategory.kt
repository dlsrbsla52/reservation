package com.media.bus.contract.entity.member

import com.media.bus.common.entity.common.BaseEnum

/**
 * ## 회원 유형을 큰 카테고리로 분류하는 Enum
 *
 * [MemberType]마다 하나의 카테고리를 가지며,
 * `@Authorize.categories()` 조건 매칭에 사용됩니다.
 * 예) ADMIN 카테고리 전체를 허용하면 ADMIN_USER / ADMIN_MASTER / ADMIN_DEVELOPER 모두 통과.
 */
@Suppress("unused")
enum class MemberCategory(
    override val displayName: String,
) : BaseEnum {
    USER("일반 사용자"),
    BUSINESS("비즈니스"),
    ADMIN("관리자"),
    ;
}
