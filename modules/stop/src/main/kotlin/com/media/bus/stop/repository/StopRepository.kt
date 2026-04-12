package com.media.bus.stop.repository

import com.media.bus.stop.entity.StopEntity
import com.media.bus.stop.entity.StopTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * ## 정류소 저장소
 *
 * Exposed DAO 기반 조회 메서드 제공.
 * 모든 쿼리는 Spring 관리 트랜잭션 안에서 실행된다.
 */
@Repository
class StopRepository {

    @Transactional(readOnly = true)
    fun findById(pk: UUID): StopEntity? = StopEntity.findById(pk)

    @Transactional(readOnly = true)
    fun findByStopId(stopId: String): StopEntity? =
        StopEntity.find { StopTable.stopId eq stopId }.firstOrNull()

    // idx_stop_name 인덱스 활용 — 페이징 없이 전방 일치 조회
    @Transactional(readOnly = true)
    fun findByStopIdIn(stopIds: Collection<String>): List<StopEntity> =
        StopEntity.find { StopTable.stopId inList stopIds.toList() }.toList()

    @Transactional(readOnly = true)
    fun findByStopNameStartingWith(stopName: String): List<StopEntity> =
        StopEntity.find { StopTable.stopName like "$stopName%" }.toList()

    /**
     * pk(UUID) 복수 기반 일괄 조회 — 예약 서비스의 N+1 S2S 호출 제거용.
     * 빈 컬렉션은 DB 쿼리 없이 빈 리스트를 반환한다.
     */
    @Transactional(readOnly = true)
    fun findByIdIn(pks: Collection<UUID>): List<StopEntity> {
        if (pks.isEmpty()) return emptyList()
        return StopEntity.find { StopTable.id inList pks.toList() }.toList()
    }
}
