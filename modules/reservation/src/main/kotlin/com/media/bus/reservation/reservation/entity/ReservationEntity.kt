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

/** 예약 Exposed DAO 엔티티 */
class ReservationEntity(id: EntityID<UUID>) : DateBaseEntity(id, ReservationTable) {

    companion object : UUIDEntityClass<ReservationEntity>(ReservationTable) {

        /** 예약 생성 팩토리 메서드. */
        fun create(
            memberId: UUID,
            stopId: UUID,
            slotNo: Short,
            consultationRequestedAt: OffsetDateTime,
            desiredContractStartDate: LocalDate,
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

    var status by ReservationTable.status
        private set

    var holdExpiresAt by ReservationTable.holdExpiresAt
        private set

    var consultationRequestedAt by ReservationTable.consultationRequestedAt
    var desiredContractStartDate by ReservationTable.desiredContractStartDate
    var assigneeId by ReservationTable.assigneeId

    val slot: SlotRef
        get() = SlotRef(stopId, slotNo)

    /** 선점 기간이 지났는지. 스케줄러가 아직 정리하지 못한 만료 선점을 예약 시점에 가려내는 데 쓴다. */
    fun isHoldExpired(now: OffsetDateTime): Boolean =
        status.holdsSlot && holdExpiresAt?.isBefore(now) == true

    /** 상태를 바꾼다. */
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
