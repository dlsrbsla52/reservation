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

/** 페이지 단위 정류소 배치 저장 전담 서비스 */
@Service
class StopBulkPersistService {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 페이지 단위 행(row) 목록을 독립 트랜잭션에서 조회·저장·갱신한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun persistPage(rows: List<SeoulBusStopRow>): StopPagePersistResult {
        val stopIds = rows.map { it.stopsNo }
        val existingMap = StopEntity.find { StopTable.stopId inList stopIds }
            .associateBy { it.stopId }

        var savedCount = 0
        var updatedCount = 0
        var skippedCount = 0
        var failedCount = 0

        for (row in rows) {
            if (!hasValidCoordinate(row)) {
                failedCount++
                continue
            }

            val existing = existingMap[row.stopsNo]
            if (existing == null) {
                StopEntity.createFromPublicApi(row)
                savedCount++
            } else {
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

/** 페이지 단위 적재 결과 */
data class StopPagePersistResult(
    val saved: Int,
    val updated: Int,
    val skipped: Int,
    val failed: Int,
)
