package com.media.bus.contract.entity.member

import com.media.bus.common.entity.common.BaseEnum

/**
 * ## 회원 유형 구분 Enum
 *
 * 각 타입은 소속 카테고리([MemberCategory])를 가집니다.
 * 권한 정보는 DB(auth.role_permission)에서 관리되며,
 * 로그인/토큰 재발급 시 조회하여 JWT claim에 포함됩니다.
 * 하위 서비스는 X-User-Permissions 헤더로 권한 Set을 복원합니다.
 *
 * - MEMBER          : 일반 회원
 * - BUSINESS        : 비즈니스 회원
 * - ADMIN_USER      : 관리회원 일반
 * - ADMIN_MASTER    : 관리회원 마스터
 * - ADMIN_DEVELOPER : 관리회원 개발자
 */
@Suppress("unused")
enum class MemberType(
    override val displayName: String,
    val category: MemberCategory,
) : BaseEnum {
    MEMBER("일반 회원", MemberCategory.USER),
    BUSINESS("비즈니스 회원", MemberCategory.BUSINESS),
    ADMIN_USER("관리회원 일반 유저", MemberCategory.ADMIN),
    ADMIN_MASTER("관리회원 마스터(대부분의 권한을 가지고 있음)", MemberCategory.ADMIN),
    ADMIN_DEVELOPER("관리회원 개발자", MemberCategory.ADMIN),
    ;

    /** ADMIN 카테고리 여부 */
    val isAdmin: Boolean get() = category == MemberCategory.ADMIN

    /** USER 카테고리 여부 */
    val isUser: Boolean get() = category == MemberCategory.USER

    /** BUSINESS 카테고리 여부 */
    val isBusiness: Boolean get() = category == MemberCategory.BUSINESS

    companion object {
        /** Enum name으로 검색 */
        @JvmStatic
        fun fromName(name: String): MemberType? = BaseEnum.fromName<MemberType>(name)
    }
}
