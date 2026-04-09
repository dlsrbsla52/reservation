package com.media.bus.stop.dto.request

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.result.type.CommonResult
import java.util.*

/**
 * ## 정류소 단건 조회 검색 기준
 *
 * pk(UUID), stopId(정류소 번호), stopName(정류소 이름 전방 일치) 중 하나를 사용한다.
 * sealed interface + data class 조합으로 패턴 매칭을 지원한다.
 */
sealed interface StopSearchCriteria {

    /** 대리키(UUID) 기준 조회 */
    data class ByPk(val pk: UUID) : StopSearchCriteria

    /** 정류소 번호(stop_id) 기준 조회 */
    data class ByStopId(val stopId: String) : StopSearchCriteria

    /** 정류소 이름 전방 일치 조회 (idx_stop_name 인덱스 활용, 'text%' 패턴) */
    data class ByStopName(val stopName: String) : StopSearchCriteria

    companion object {
        /**
         * query param 세 값으로부터 검색 기준을 생성한다.
         * 우선순위: pk > stopId > stopName. 모두 null이면 예외.
         */
        fun of(pk: UUID?, stopId: String?, stopName: String?): StopSearchCriteria = when {
            pk != null       -> ByPk(pk)
            stopId != null   -> ByStopId(stopId)
            stopName != null -> ByStopName(stopName)
            else -> throw BusinessException(CommonResult.REQUEST_FAIL, "유효한 값을 전달해주세요.")
        }
    }
}
