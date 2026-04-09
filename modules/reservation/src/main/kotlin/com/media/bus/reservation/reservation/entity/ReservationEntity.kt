package com.media.bus.reservation.reservation.entity

import com.media.bus.common.entity.common.DateBaseEntity
import com.media.bus.common.entity.common.UuidV7
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
         * 예약 생성 팩토리 메서드.
         *
         * @param memberId                  예약 회원 ID
         * @param stopId                    예약 정류소 ID
         * @param consultationRequestedAt   상담 요청 일시
         * @param desiredContractStartDate  희망 계약 시작일
         */
        fun create(
            memberId: UUID,
            stopId: UUID,
            consultationRequestedAt: OffsetDateTime,
            desiredContractStartDate: LocalDate?,
        ): ReservationEntity = new(UuidV7.generate()) {
            this.memberId = memberId
            this.stopId = stopId
            this.consultationRequestedAt = consultationRequestedAt
            this.desiredContractStartDate = desiredContractStartDate
        }
    }

    var memberId by ReservationTable.memberId
    var stopId by ReservationTable.stopId
    var consultationRequestedAt by ReservationTable.consultationRequestedAt
    var desiredContractStartDate by ReservationTable.desiredContractStartDate
}
