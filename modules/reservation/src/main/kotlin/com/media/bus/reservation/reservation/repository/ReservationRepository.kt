package com.media.bus.reservation.reservation.repository

import com.media.bus.reservation.reservation.entity.ReservationConsultationTable
import com.media.bus.reservation.reservation.entity.ReservationEntity
import com.media.bus.reservation.reservation.entity.ReservationTable
import com.media.bus.reservation.reservation.entity.enums.ReservationStatus
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.*

/**
 * ## 예약 저장소
 *
 * Exposed DAO 기반 예약 조회 메서드를 제공한다.
 * 저장은 `ReservationEntity.create()` 팩토리 메서드가 트랜잭션 내에서 자동 INSERT 한다.
 */
@Repository
class ReservationRepository {

    @Transactional(readOnly = true)
    fun findById(id: UUID): ReservationEntity? = ReservationEntity.findById(id)

    /** 본인 소유 예약만 조회. 다른 회원이 예약을 소유한 경우 null 을 반환하여 인가 검증에 사용한다. */
    @Transactional(readOnly = true)
    fun findByIdAndMemberId(id: UUID, memberId: UUID): ReservationEntity? =
        ReservationEntity.find {
            (ReservationTable.id eq id) and (ReservationTable.memberId eq memberId)
        }.firstOrNull()

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

    /**
     * 동일 회원·정류소에 대해 현재 "진행 중"(PENDING/CONSULTING) 상태의 예약이 존재하는지 확인한다.
     *
     * 판정 근거: 각 예약의 가장 최신 `reservation_consultation.status` 가 진행 중 상태인지.
     * 동일 조합에 대한 중복 예약 생성을 사전 차단하기 위해 저장 직전에 호출한다.
     */
    @Transactional(readOnly = true)
    fun existsActiveByMemberAndStop(memberId: UUID, stopId: UUID): Boolean {
        val candidates = ReservationEntity.find {
            (ReservationTable.memberId eq memberId) and (ReservationTable.stopId eq stopId)
        }
        // 최신 상담 row 의 상태가 진행 중(PENDING/CONSULTING)이면 중복으로 간주
        return candidates.any { reservation ->
            val latestStatus = ReservationConsultationTable
                .selectAll()
                .where { ReservationConsultationTable.reservationId eq reservation.id }
                .orderBy(ReservationConsultationTable.createdAt to SortOrder.DESC)
                .limit(1)
                .firstOrNull()
                ?.get(ReservationConsultationTable.status)
            latestStatus == ReservationStatus.PENDING || latestStatus == ReservationStatus.CONSULTING
        }
    }

    /**
     * 어드민용 전체 예약 목록을 검색 및 조건부 필터링하여 페이징 조회한다.
     * Left Join newer is null 패턴을 활용하여 최신 상담 이력의 상태를 N+1 쿼리 없이 한번에 결합한다.
     */
    @Transactional(readOnly = true)
    fun searchAdminReservations(
        status: ReservationStatus?,
        assigneeId: UUID?,
        stopId: UUID?,
        createdFrom: OffsetDateTime?,
        createdTo: OffsetDateTime?,
        page: Int,
        size: Int
    ): List<Pair<ReservationEntity, ReservationStatus>> {
        val rc = ReservationConsultationTable
        val rcNewer = ReservationConsultationTable.alias("rc_newer")

        val joinRelation = ReservationTable
            .join(
                otherTable = rc,
                joinType = JoinType.INNER,
                onColumn = ReservationTable.id,
                otherColumn = rc.reservationId
            )
            .join(
                otherTable = rcNewer,
                joinType = JoinType.LEFT,
                onColumn = ReservationTable.id,
                otherColumn = rcNewer[ReservationConsultationTable.reservationId],
                additionalConstraint = {
                    rc.createdAt less rcNewer[ReservationConsultationTable.createdAt]
                }
            )

        val conditions = mutableListOf<Op<Boolean>>()
        conditions.add(rcNewer[ReservationConsultationTable.id].isNull())

        status?.let { conditions.add(rc.status eq it) }
        assigneeId?.let { conditions.add(ReservationTable.assigneeId eq it) }
        stopId?.let { conditions.add(ReservationTable.stopId eq it) }
        createdFrom?.let { conditions.add(ReservationTable.createdAt greaterEq it) }
        createdTo?.let { conditions.add(ReservationTable.createdAt lessEq it) }

        val combinedOp = conditions.reduce { acc, op -> acc and op }

        val query = joinRelation.selectAll().where(combinedOp)
            .orderBy(ReservationTable.createdAt to SortOrder.DESC)
            .limit(size)
            .offset(start = (page * size).toLong())

        return query.map { row ->
            val entity = ReservationEntity.wrapRow(row)
            val currentStatus = row[rc.status]
            entity to currentStatus
        }
    }

    /** 어드민 검색 조건에 매칭되는 전체 예약 개수를 반환한다. */
    @Transactional(readOnly = true)
    fun countAdminReservations(
        status: ReservationStatus?,
        assigneeId: UUID?,
        stopId: UUID?,
        createdFrom: OffsetDateTime?,
        createdTo: OffsetDateTime?
    ): Long {
        val rc = ReservationConsultationTable
        val rcNewer = ReservationConsultationTable.alias("rc_newer")

        val joinRelation = ReservationTable
            .join(
                otherTable = rc,
                joinType = JoinType.INNER,
                onColumn = ReservationTable.id,
                otherColumn = rc.reservationId
            )
            .join(
                otherTable = rcNewer,
                joinType = JoinType.LEFT,
                onColumn = ReservationTable.id,
                otherColumn = rcNewer[ReservationConsultationTable.reservationId],
                additionalConstraint = {
                    rc.createdAt less rcNewer[ReservationConsultationTable.createdAt]
                }
            )

        val conditions = mutableListOf<Op<Boolean>>()
        conditions.add(rcNewer[ReservationConsultationTable.id].isNull())

        status?.let { conditions.add(rc.status eq it) }
        assigneeId?.let { conditions.add(ReservationTable.assigneeId eq it) }
        stopId?.let { conditions.add(ReservationTable.stopId eq it) }
        createdFrom?.let { conditions.add(ReservationTable.createdAt greaterEq it) }
        createdTo?.let { conditions.add(ReservationTable.createdAt lessEq it) }

        val combinedOp = conditions.reduce { acc, op -> acc and op }

        return joinRelation.selectAll().where(combinedOp).count()
    }
}
