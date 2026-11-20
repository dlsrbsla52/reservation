package com.media.bus.stop.repository

import com.media.bus.stop.entity.StopEntity
import com.media.bus.stop.entity.StopTable
import com.media.bus.stop.entity.enums.StopType
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.*

/** 정류소 저장소 */
@Repository
class StopRepository {

    @Transactional(readOnly = true)
    fun findById(pk: UUID): StopEntity? = StopEntity.findById(pk)

    @Transactional(readOnly = true)
    fun findByStopId(stopId: String): StopEntity? =
        StopEntity.find { StopTable.stopId eq stopId }.firstOrNull()

    @Transactional(readOnly = true)
    fun findByStopIdIn(stopIds: Collection<String>): List<StopEntity> =
        StopEntity.find { StopTable.stopId inList stopIds.toList() }.toList()

    @Transactional(readOnly = true)
    fun findByStopNameStartingWith(stopName: String): List<StopEntity> =
        StopEntity.find { StopTable.stopName like "${escapeLike(stopName)}%" }
            .limit(MAX_NAME_RESULTS + 1).toList()

    /** pk(UUID) 복수 기반 일괄 조회 — 예약 모듈의 N+1 정류소 조회 제거용. */
    @Transactional(readOnly = true)
    fun findByIdIn(pks: Collection<UUID>): List<StopEntity> {
        if (pks.isEmpty()) return emptyList()
        return StopEntity.find { StopTable.id inList pks.toList() }.toList()
    }

    /** 목록 조회 술어를 한 곳에서 만든다. */
    private fun listPredicate(keyword: String?, stopsType: StopType?): Op<Boolean> {
        val conditions = mutableListOf<Op<Boolean>>()
        keyword?.let {
            conditions += (StopTable.stopName like "%${escapeLike(it)}%") or (StopTable.stopId like "%${escapeLike(it)}%")
        }
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

    companion object {
        const val MAX_NAME_RESULTS = 100

        /** PostgreSQL의 기본 LIKE 이스케이프 문자부터 처리하여 리터럴 접두·부분 검색을 보장한다. */
        internal fun escapeLike(value: String): String =
            value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    }
}
