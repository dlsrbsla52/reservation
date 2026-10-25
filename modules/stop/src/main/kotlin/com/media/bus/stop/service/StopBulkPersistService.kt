package com.media.bus.stop.service

import com.media.bus.stop.dto.external.SeoulBusStopRow
import com.media.bus.stop.entity.StopEntity
import com.media.bus.stop.entity.StopTable
import com.media.bus.stop.entity.enums.ChangeSource
import com.media.bus.stop.support.Coordinate
import org.jetbrains.exposed.v1.core.inList
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * ## 페이지 단위 정류소 배치 저장 전담 서비스
 *
 * `REQUIRES_NEW` 전파 속성으로 독립 트랜잭션을 생성하여 페이지별 커밋을 보장한다.
 * 중간 실패 시 해당 페이지만 롤백되며, 이전 페이지의 커밋은 유지된다.
 *
 * **주의:** self-invocation AOP 우회를 방지하기 위해 `StopRegisterService`에서 분리된 별도 빈이다.
 *
 * DB 조회와 생성을 모두 이 서비스 내부에서 수행하여 엔티티 생명주기가
 * 단일 트랜잭션 경계 안에 머물도록 보장한다.
 */
@Service
class StopBulkPersistService {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 페이지 단위 행(row) 목록을 독립 트랜잭션에서 조회·저장·갱신한다.
     *
     * 좌표가 숫자로 파싱되지 않거나 위경도 범위를 벗어난 행은 **등록을 거부하고 건너뛴다.**
     * 예외를 던지지 않는 이유는 공공 API 한 건의 불량 데이터로 페이지 전체(최대 1,000건)가
     * 롤백되는 것을 막기 위함이다. 거부된 행은 WARN 로그로 남기고 `failed` 수에 집계한다.
     *
     * @param rows 서울 공공 API에서 가져온 한 페이지 분량의 정류소 데이터
     * @return 페이지 처리 결과 집계
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun persistPage(rows: List<SeoulBusStopRow>): StopPagePersistResult {
        // 해당 페이지의 stopId 목록을 DB에서 한 번에 조회
        val stopIds = rows.map { it.stopsNo }
        val existingMap = StopEntity.find { StopTable.stopId inList stopIds }
            .associateBy { it.stopId }

        var savedCount = 0
        var updatedCount = 0
        var skippedCount = 0
        var failedCount = 0

        for (row in rows) {
            // 좌표 사전 검증 — 통과한 행만 엔티티 팩토리로 넘긴다
            if (!hasValidCoordinate(row)) {
                failedCount++
                continue
            }

            val existing = existingMap[row.stopsNo]
            if (existing == null) {
                // 신규 — Exposed new() 호출 시 트랜잭션 커밋 시 자동 INSERT
                StopEntity.createFromPublicApi(row)
                savedCount++
            } else {
                // 기존 — 변경 있으면 history 생성 + 필드 갱신, 변경 없으면 null 반환
                if (existing.applyUpdate(row, ChangeSource.SYSTEM) != null) {
                    updatedCount++
                } else {
                    skippedCount++
                }
            }
        }

        return StopPagePersistResult(savedCount, updatedCount, skippedCount, failedCount)
    }

    /** 좌표가 유효하지 않으면 사유를 로그로 남기고 false를 반환한다. */
    private fun hasValidCoordinate(row: SeoulBusStopRow): Boolean {
        val validX = Coordinate.parseOrNullLongitude(row.xCrd) != null
        val validY = Coordinate.parseOrNullLatitude(row.yCrd) != null
        if (validX && validY) return true

        log.warn(
            "좌표 불량으로 정류소 등록을 건너뜁니다 — stopId={}, stopName={}, xCrd={}, yCrd={}",
            row.stopsNo, row.stopsName, row.xCrd, row.yCrd,
        )
        return false
    }
}

/**
 * ## 페이지 단위 적재 결과
 *
 * `failed`는 좌표 불량 등으로 **등록이 거부된** 행 수다.
 * 변경이 없어 건너뛴 `skipped`와는 의미가 다르므로 분리해서 집계한다.
 */
data class StopPagePersistResult(
    val saved: Int,
    val updated: Int,
    val skipped: Int,
    val failed: Int,
)
