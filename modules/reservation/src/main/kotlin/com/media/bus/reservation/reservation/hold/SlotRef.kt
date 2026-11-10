package com.media.bus.reservation.reservation.hold

import java.util.*

/** 선점 단위(정류소 + 광고면 번호). 트랜잭션이 끝난 뒤 Redis 점유 캐시를 지울 대상을 전달하는 데 쓴다. */
data class SlotRef(
    val stopId: UUID,
    val slotNo: Short,
)
