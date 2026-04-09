package com.media.bus.stop.service

import com.media.bus.stop.dto.external.SeoulBusStopRow
import com.media.bus.stop.entity.StopEntity
import com.media.bus.stop.entity.StopTable
import com.media.bus.stop.entity.enums.ChangeSource
import org.jetbrains.exposed.v1.core.inList
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

    /**
     * 페이지 단위 행(row) 목록을 독립 트랜잭션에서 조회·저장·갱신한다.
     *
     * @param rows 서울 공공 API에서 가져온 한 페이지 분량의 정류소 데이터
     * @return (신규 저장 수, 업데이트 수, 변경 없음 수)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun persistPage(rows: List<SeoulBusStopRow>): Triple<Int, Int, Int> {
        // 해당 페이지의 stopId 목록을 DB에서 한 번에 조회
        val stopIds = rows.map { it.stopsNo }
        val existingMap = StopEntity.find { StopTable.stopId inList stopIds }
            .associateBy { it.stopId }

        var savedCount = 0
        var updatedCount = 0
        var skippedCount = 0

        for (row in rows) {
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

        return Triple(savedCount, updatedCount, skippedCount)
    }
}
