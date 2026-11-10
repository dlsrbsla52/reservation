package com.media.bus.reservation.reservation.entity.enums

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 예약 상태 전이와 선점 판정의 계약.
 * `holdsSlot` 은 DB 부분 유니크 인덱스(`status IN ('PENDING','CONSULTING')`)와 같은 집합이어야 한다.
 */
class ReservationStatusTest {

    @Test
    fun `선점 상태는 PENDING 과 CONSULTING 뿐이다`() {
        assertThat(ReservationStatus.HOLDING)
            .containsExactlyInAnyOrder(ReservationStatus.PENDING, ReservationStatus.CONSULTING)
    }

    @Test
    fun `선점 상태에서만 만료될 수 있다`() {
        assertThat(ReservationStatus.entries.filter { it.canTransitionTo(ReservationStatus.EXPIRED) })
            .containsExactlyInAnyOrder(ReservationStatus.PENDING, ReservationStatus.CONSULTING)
    }

    @Test
    fun `종료 상태에서는 어떤 상태로도 전이할 수 없다`() {
        ReservationStatus.entries.filterNot { it.holdsSlot }.forEach { terminal ->
            assertThat(ReservationStatus.entries.none { terminal.canTransitionTo(it) })
                .describedAs("%s 는 종료 상태", terminal)
                .isTrue()
        }
    }

    @Test
    fun `취소는 상담 전에만 가능하다`() {
        assertThat(ReservationStatus.PENDING.canTransitionTo(ReservationStatus.CANCELLED)).isTrue()
        assertThat(ReservationStatus.CONSULTING.canTransitionTo(ReservationStatus.CANCELLED)).isFalse()
    }
}
