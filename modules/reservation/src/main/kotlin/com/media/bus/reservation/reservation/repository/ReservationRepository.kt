package com.media.bus.reservation.reservation.repository

import com.media.bus.reservation.reservation.entity.ReservationConsultationTable
import com.media.bus.reservation.reservation.entity.ReservationEntity
import com.media.bus.reservation.reservation.entity.ReservationTable
import com.media.bus.reservation.reservation.entity.enums.ReservationStatus
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
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
}
