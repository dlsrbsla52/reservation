package com.media.bus.stop.service

import com.media.bus.stop.client.SeoulBusApiClient
import com.media.bus.stop.dto.response.StopBulkRegisterResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class StopRegisterService(
    private val seoulBusApiClient: SeoulBusApiClient,
    private val stopBulkPersistService: StopBulkPersistService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 서울 열린데이터광장 공공 API에서 전체 버스 정류소를 가져와 DB에 저장한다.
     * - 신규 stopId → insert
     * - 기존 stopId + 필드 변경 → Stop 업데이트 + StopUpdateHistory 기록
     * - 기존 stopId + 변경 없음 → 건너뜀
     *
     * `@Transactional` 없음 — `StopBulkPersistService`가 `REQUIRES_NEW`로 페이지별 독립 트랜잭션을 생성한다.
     * 전체 루프를 단일 트랜잭션으로 묶으면 장시간 DB 커넥션 점유 및 중간 실패 시 전체 롤백이 발생한다.
     */
    fun registerAllFromPublicApi(): StopBulkRegisterResult {
        val totalCount = seoulBusApiClient.fetchTotalCount()
        val pageSize = seoulBusApiClient.pageSize
        log.info("서울 공공 API 전체 정류소 수: {}", totalCount)

        var savedCount = 0
        var updatedCount = 0
        var skippedCount = 0

        var start = 1
        while (start <= totalCount) {
            val end = minOf(start + pageSize - 1, totalCount)
            val rows = seoulBusApiClient.fetchStops(start, end)

            // 페이지 단위 독립 트랜잭션 커밋 — 중간 실패 시 해당 페이지만 롤백
            val (s, u, sk) = stopBulkPersistService.persistPage(rows)
            savedCount += s
            updatedCount += u
            skippedCount += sk

            log.info("진행: {}/{} — 신규 {}건, 업데이트 {}건, 변경없음 {}건", end, totalCount, s, u, sk)
            start += pageSize
        }

        log.info("일괄 등록 완료 — 저장: {}건, 업데이트: {}건, 건너뜀: {}건", savedCount, updatedCount, skippedCount)
        return StopBulkRegisterResult(savedCount, updatedCount, skippedCount, totalCount)
    }
}
