package com.media.bus.stop.entity.enums

import com.media.bus.common.entity.common.BaseEnum

/**
 * ## 정류소 유형 Enum
 *
 * 서울 열린데이터광장 공공 API의 `STOPS_TYPE` 필드에 대응한다.
 * `displayName`은 공공 API 원본 한글 값이며 역방향 조회에 사용된다.
 */
@Suppress("unused")
enum class StopType(
    override val displayName: String,
) : BaseEnum {
    VILLAGE_BUS("마을버스"),
    ROADSIDE_ALL_DAY("가로변전일"),
    ROADSIDE_TIMED("가로변시간"),
    CENTER_LANE("중앙차로"),
    GENERAL_LANE("일반차로"),
    HANGANG_DOCK("한강선착장"),
    ;

    companion object {
        // displayName -> StopType 역방향 조회를 위한 O(1) 캐시 (공공 API 연동 시 빈번히 호출됨)
        private val byDisplayName = entries.associateBy { it.displayName }

        fun fromName(name: String): StopType? = BaseEnum.fromName<StopType>(name)

        fun fromDisplayName(displayName: String): StopType =
            byDisplayName[displayName] ?: error("알 수 없는 정류소 유형: $displayName")
    }
}
