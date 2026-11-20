package com.media.bus.reservation.reservation.repository

import com.media.bus.reservation.reservation.entity.ReservationEntity
import com.media.bus.reservation.reservation.entity.ReservationTable
import com.media.bus.reservation.reservation.entity.enums.ReservationStatus
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.*

/** 예약 저장소 */
@Repository
class ReservationRepository {

    @Transactional(readOnly = true)
    fun findById(id: UUID): ReservationEntity? = ReservationEntity.findById(id)

    /** 상태 전이용 조회. 같은 예약을 동시에 바꾸려는 요청을 순서대로 세운다. */
    @Transactional(propagation = Propagation.MANDATORY)
    fun findByIdForUpdate(id: UUID): ReservationEntity? =
        ReservationEntity.find { ReservationTable.id eq id }.forUpdate().firstOrNull()

    /** 본인 소유 예약만 잠금 조회. 다른 회원의 예약이면 null 을 반환하여 인가 검증에 사용한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    fun findByIdAndMemberIdForUpdate(id: UUID, memberId: UUID): ReservationEntity? =
        ReservationEntity.find {
            (ReservationTable.id eq id) and (ReservationTable.memberId eq memberId)
        }.forUpdate().firstOrNull()

    /** 회원의 예약 수. 페이지네이션 메타에 사용한다. */
    @Transactional(readOnly = true)
    fun countByMemberId(memberId: UUID): Long =
        ReservationTable.selectAll().where { ReservationTable.memberId eq memberId }.count()

    /** 회원의 예약 목록을 생성일 내림차순으로 페이지 조회한다. */
    @Transactional(readOnly = true)
    fun findByMemberIdPaged(memberId: UUID, page: Int, size: Int): List<ReservationEntity> =
        ReservationEntity.find { ReservationTable.memberId eq memberId }
            .orderBy(ReservationTable.createdAt to SortOrder.DESC)
            .limit(size)
            .offset(start = (page * size).toLong())
            .toList()

    /** 회원이 현재 선점 중인 예약 수. 회원당 선점 한도 판정에 쓴다. */
    @Transactional(readOnly = true)
    fun countHoldingByMember(memberId: UUID): Long =
        ReservationTable.selectAll()
            .where { (ReservationTable.memberId eq memberId) and (ReservationTable.status inList ReservationStatus.HOLDING) }
            .count()

    /** 광고면을 현재 선점 중인 예약을 잠금 조회한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    fun findHolderForUpdate(stopId: UUID, slotNo: Short): ReservationEntity? =
        ReservationEntity.find {
            (ReservationTable.stopId eq stopId) and
                (ReservationTable.slotNo eq slotNo) and
                (ReservationTable.status inList ReservationStatus.HOLDING)
        }.forUpdate().firstOrNull()

    /** 선점 기간이 지난 예약을 만료가 오래된 순으로 잠금 조회한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    fun findExpiredHoldsForUpdate(now: OffsetDateTime, limit: Int): List<ReservationEntity> =
        ReservationEntity.find {
            (ReservationTable.status inList ReservationStatus.HOLDING) and (ReservationTable.holdExpiresAt less now)
        }
            .orderBy(ReservationTable.holdExpiresAt to SortOrder.ASC)
            .limit(limit)
            .forUpdate(ForUpdateOption.PostgreSQL.ForUpdate(ForUpdateOption.PostgreSQL.MODE.SKIP_LOCKED))
            .toList()

    /** 어드민용 전체 예약 목록을 조건으로 걸러 최신 생성순으로 페이지 조회한다. */
    @Transactional(readOnly = true)
    fun searchAdminReservations(
        status: ReservationStatus?,
        assigneeId: UUID?,
        stopId: UUID?,
        createdFrom: OffsetDateTime?,
        createdTo: OffsetDateTime?,
        page: Int,
        size: Int,
    ): List<ReservationEntity> =
        ReservationEntity.find { adminSearchPredicate(status, assigneeId, stopId, createdFrom, createdTo) }
            .orderBy(ReservationTable.createdAt to SortOrder.DESC)
            .limit(size)
            .offset(start = (page * size).toLong())
            .toList()

    /** 어드민 검색 조건에 매칭되는 전체 예약 개수를 반환한다. 목록 쿼리와 같은 조건을 쓴다. */
    @Transactional(readOnly = true)
    fun countAdminReservations(
        status: ReservationStatus?,
        assigneeId: UUID?,
        stopId: UUID?,
        createdFrom: OffsetDateTime?,
        createdTo: OffsetDateTime?,
    ): Long =
        ReservationTable.selectAll()
            .where { adminSearchPredicate(status, assigneeId, stopId, createdFrom, createdTo) }
            .count()

    private fun adminSearchPredicate(
        status: ReservationStatus?,
        assigneeId: UUID?,
        stopId: UUID?,
        createdFrom: OffsetDateTime?,
        createdTo: OffsetDateTime?,
    ): Op<Boolean> {
        val conditions = mutableListOf<Op<Boolean>>()
        status?.let { conditions += ReservationTable.status eq it }
        assigneeId?.let { conditions += ReservationTable.assigneeId eq it }
        stopId?.let { conditions += ReservationTable.stopId eq it }
        createdFrom?.let { conditions += ReservationTable.createdAt greaterEq it }
        createdTo?.let { conditions += ReservationTable.createdAt lessEq it }
        return conditions.reduceOrNull { acc, op -> acc and op } ?: Op.TRUE
    }
}
