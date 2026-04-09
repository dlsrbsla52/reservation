package com.media.bus.stop.entity.enums

import com.media.bus.common.entity.common.BaseEnum

/**
 * ## 변경 주체 Enum
 *
 * 정류소 데이터의 등록/변경 주체를 구분한다.
 * - `USER`: 관리자가 수동으로 등록/수정
 * - `SYSTEM`: 공공 API 연동에 의한 자동 등록/수정
 */
@Suppress("unused")
enum class ChangeSource(
    override val displayName: String,
) : BaseEnum {
    USER("사용자"),
    SYSTEM("시스템"),
}
