package com.media.bus.stop.map.service

import com.media.bus.stop.map.dto.StopBoundsCriteria
import com.media.bus.stop.map.dto.StopBoundsResponse
import com.media.bus.stop.map.repository.StopMapRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * ## 지도 조회 전용 서비스 (읽기 전용 슬라이스)
 *
 * `StopService` 와 분리한 이유는 두 서비스의 요구가 정반대이기 때문이다.
 * - `StopService` — 커맨드 가드, 변경 이력, 단가 결합 등 **정합성**이 우선
 * - 이 서비스 — 인덱스 범위 스캔 한 번으로 끝나는 **응답 속도와 페이로드 크기**가 우선
 *
 * 단가(`StopPrice`)를 의도적으로 붙이지 않는다. 마커 표시에 필요 없고,
 * 수천 건 조회에 단가 결합을 추가하면 지도 전용 DTO 를 만든 이유가 사라진다.
 * 마커 클릭 시 상세 정보는 기존 `GET /api/v1/stop?pk=...` 로 별도 조회한다.
 */
@Service
class StopMapQueryService(
    private val stopMapRepository: StopMapRepository,
) {

    /**
     * 지도 영역에 포함된 정류소 마커를 조회한다.
     *
     * Repository 는 잘림 판정을 위해 `limit + 1` 건을 읽으므로,
     * 여기서 초과분을 잘라내고 `truncated` 플래그로 변환한다.
     */
    @Transactional(readOnly = true)
    fun findMarkersInBounds(criteria: StopBoundsCriteria): StopBoundsResponse {
        val rows = stopMapRepository.findMarkersInBounds(criteria)
        val truncated = rows.size > criteria.limit

        return StopBoundsResponse(
            items = if (truncated) rows.take(criteria.limit) else rows,
            truncated = truncated,
            limit = criteria.limit,
        )
    }
}
