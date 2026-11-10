package com.media.bus.reservation.reservation.entity

import com.media.bus.common.entity.common.DateBaseEntity
import com.media.bus.common.entity.common.UuidV7
import com.media.bus.reservation.reservation.entity.enums.ReservationStatus
import com.media.bus.reservation.reservation.hold.SlotRef
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

/**
 * ## 예약 Exposed DAO 엔티티
 *
 * 예약 정보를 담는 DAO 엔티티.
 * `create()` 팩토리 메서드로만 생성을 허용한다.
 */
class ReservationEntity(id: EntityID<UUID>) : DateBaseEntity(id, ReservationTable) {

    companion object : UUIDEntityClass<ReservationEntity>(ReservationTable) {

        /**
         * 예약 생성 팩토리 메서드. 생성과 동시에 `PENDING` 상태로 광고면을 선점한다.
         *
         * @param memberId                  예약 회원 ID
         * @param stopId                    예약 정류소 ID
         * @param slotNo                    정류소 내 광고면 번호
         * @param consultationRequestedAt   상담 요청 일시
         * @param desiredContractStartDate  희망 계약 시작일
         * @param holdExpiresAt             선점 만료 일시
         */
        fun create(
            memberId: UUID,
            stopId: UUID,
            slotNo: Short,
            consultationRequestedAt: OffsetDateTime,
            desiredContractStartDate: LocalDate?,
            holdExpiresAt: OffsetDateTime,
        ): ReservationEntity = new(UuidV7.generate()) {
            this.memberId = memberId
            this.stopId = stopId
            this.slotNo = slotNo
            this.status = ReservationStatus.PENDING
            this.holdExpiresAt = holdExpiresAt
            this.consultationRequestedAt = consultationRequestedAt
            this.desiredContractStartDate = desiredContractStartDate
        }
    }

    var memberId by ReservationTable.memberId
    var stopId by ReservationTable.stopId
    var slotNo by ReservationTable.slotNo

    /** 현재 상태. 변경은 [changeStatus] 로만 한다(이력 row 와 함께 바뀌어야 하므로 서비스가 호출). */
    var status by ReservationTable.status
        private set

    /** 선점 만료 일시. 진행 중 상태에서만 값이 있고, 종료 상태에서는 null 이다. */
    var holdExpiresAt by ReservationTable.holdExpiresAt
        private set

    var consultationRequestedAt by ReservationTable.consultationRequestedAt
    var desiredContractStartDate by ReservationTable.desiredContractStartDate
    var assigneeId by ReservationTable.assigneeId

    /** 이 예약이 선점하는 광고면. */
    val slot: SlotRef
        get() = SlotRef(stopId, slotNo)

    /** 선점 기간이 지났는지. 스케줄러가 아직 정리하지 못한 만료 선점을 예약 시점에 가려내는 데 쓴다. */
    fun isHoldExpired(now: OffsetDateTime): Boolean =
        status.holdsSlot && holdExpiresAt?.isBefore(now) == true

    /**
     * 상태를 바꾼다. 전이 가능 여부는 호출 측이 행 잠금을 잡은 상태에서 먼저 검증한다.
     *
     * @param target         바꿀 상태
     * @param holdExpiresAt  새 선점 만료 일시. 종료 상태로 가면 선점이 풀리므로 무시하고 null 로 둔다.
     */
    fun changeStatus(target: ReservationStatus, holdExpiresAt: OffsetDateTime?, now: OffsetDateTime) {
        this.status = target
        this.holdExpiresAt = if (target.holdsSlot) holdExpiresAt else null
        this.updatedAt = now
    }

    /** 선점 만료 일시만 바꾼다(관리자 연장). */
    fun extendHold(holdExpiresAt: OffsetDateTime, now: OffsetDateTime) {
        this.holdExpiresAt = holdExpiresAt
        this.updatedAt = now
    }
}
