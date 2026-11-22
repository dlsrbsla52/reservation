package com.media.bus.stop.entity.enums

import com.media.bus.common.entity.common.BaseEnum
import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.exceptions.ServiceException
import com.media.bus.stop.result.StopResult
import org.slf4j.LoggerFactory

/** 정류소 유형 Enum */
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
        private val log = LoggerFactory.getLogger(StopType::class.java)

        private val byDisplayName = entries.associateBy { it.displayName }

        fun fromName(name: String): StopType? = BaseEnum.fromName<StopType>(name)

        fun fromDisplayName(displayName: String): StopType =
            byDisplayName[displayName] ?: run {
                log.error("[StopType] 알 수 없는 정류소 유형: {}", displayName)
                throw ServiceException(StopResult.UNKNOWN_STOP_TYPE)
            }

        /** 조회 필터 파라미터를 파싱한다. */
        fun parseFilter(raw: String?): StopType? {
            val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return fromName(value) ?: throw BusinessException(StopResult.INVALID_STOP_TYPE_FILTER)
        }
    }
}
