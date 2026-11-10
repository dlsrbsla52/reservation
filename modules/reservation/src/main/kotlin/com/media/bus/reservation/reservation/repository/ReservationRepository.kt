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

/**
 * ## 예약 저장소
 *
 * Exposed DAO 기반 예약 조회 메서드를 제공한다.
 * 저장은 `ReservationEntity.create()` 팩토리 메서드가 트랜잭션 내에서 자동 INSERT 한다.
 *
 * 이름이 `ForUpdate` 로 끝나는 메서드는 행 잠금(`SELECT ... FOR UPDATE`)을 잡는다.
 * 잠금은 트랜잭션이 끝날 때 풀리므로 반드시 서비스의 쓰기 트랜잭션 안에서 호출해야 하고,
 * 그래서 전파 속성을 `MANDATORY` 로 둔다(트랜잭션 없이 부르면 즉시 실패).
 */
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

    /**
     * 광고면을 현재 선점 중인 예약을 잠금 조회한다. 부분 유니크 인덱스 덕분에 많아야 1건이다.
     *
     * 선점 예약이 없으면 잠글 행이 없어 동시 요청이 함께 통과하지만,
     * 그 경우의 승자는 INSERT 시점에 유니크 인덱스가 정한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun findHolderForUpdate(stopId: UUID, slotNo: Short): ReservationEntity? =
        ReservationEntity.find {
            (ReservationTable.stopId eq stopId) and
                (ReservationTable.slotNo eq slotNo) and
                (ReservationTable.status inList ReservationStatus.HOLDING)
        }.forUpdate().firstOrNull()

    /**
     * 선점 기간이 지난 예약을 만료가 오래된 순으로 잠금 조회한다.
     *
     * `SKIP LOCKED` — 다른 트랜잭션(다른 인스턴스의 스케줄러, 같은 예약을 처리 중인 관리자 요청)이
     * 잡고 있는 행은 기다리지 않고 건너뛴다. 건너뛴 행은 다음 주기에 다시 대상이 된다.
     */
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
