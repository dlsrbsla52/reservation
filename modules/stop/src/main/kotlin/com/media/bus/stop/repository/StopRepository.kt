package com.media.bus.stop.repository

import com.media.bus.stop.entity.StopEntity
import com.media.bus.stop.entity.StopTable
import com.media.bus.stop.entity.enums.StopType
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.selectAll
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

    /**
     * 목록 조회 술어를 한 곳에서 만든다.
     *
     * 조건 조합마다(keyword 유무 × stopsType 유무) 메서드를 따로 두면 개수가 배로 늘고,
     * 무엇보다 **목록 쿼리와 카운트 쿼리의 조건이 어긋나** 총 건수가 실제 결과와 달라지기 쉽다.
     * 두 쿼리가 반드시 같은 술어를 쓰도록 여기로 모은다.
     *
     * 조건이 하나도 없으면 `Op.TRUE` 라 `selectAll()` 과 동일한 실행 계획이 나온다.
     */
    private fun listPredicate(keyword: String?, stopsType: StopType?): Op<Boolean> {
        val conditions = mutableListOf<Op<Boolean>>()
        keyword?.let {
            conditions += (StopTable.stopName like "%$it%") or (StopTable.stopId like "%$it%")
        }
        // 유형은 enum 컬럼이라 동등 비교 한 번이면 된다.
        stopsType?.let { conditions += StopTable.stopsType eq it }
        return conditions.reduceOrNull { acc, op -> acc and op } ?: Op.TRUE
    }

    /** 목록 조건에 맞는 정류소 수. [findListPaged] 와 동일한 술어를 쓴다. */
    @Transactional(readOnly = true)
    fun countList(keyword: String?, stopsType: StopType?): Long =
        StopTable.selectAll().where { listPredicate(keyword, stopsType) }.count()

    /** 목록 조건에 맞는 정류소를 페이지네이션으로 조회 (이름 오름차순). */
    @Transactional(readOnly = true)
    fun findListPaged(keyword: String?, stopsType: StopType?, page: Int, size: Int): List<StopEntity> =
        StopTable.selectAll()
            .where { listPredicate(keyword, stopsType) }
            .orderBy(StopTable.stopName to SortOrder.ASC)
            .limit(size)
            .offset((page.toLong()) * size)
            .map { StopEntity.wrapRow(it) }
}
