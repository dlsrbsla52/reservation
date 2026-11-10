package com.media.bus.reservation.reservation.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.exceptions.StorageException
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.contract.security.MemberPrincipal
import com.media.bus.reservation.reservation.dto.request.CreateStopReservationRequest
import com.media.bus.reservation.reservation.dto.response.MyReservationResponse
import com.media.bus.reservation.reservation.dto.response.StopInfo
import com.media.bus.reservation.reservation.entity.ReservationEntity
import com.media.bus.reservation.reservation.entity.enums.ReservationStatus
import com.media.bus.reservation.reservation.hold.SlotRef
import com.media.bus.reservation.reservation.hold.StopHoldGate
import com.media.bus.reservation.reservation.result.ReservationResult
import com.media.bus.reservation.slot.StopSlot
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.OffsetDateTime
import java.util.*

/**
 * ## ReservationFacade 단위 테스트
 *
 * - 예약 생성: Redis 게이트 → stop S2S → DB 저장 순서와, 정류소별 부분 성공/실패 집계를 검증한다.
 * - 선점 해제: 취소·상태 변경·만료가 커밋된 뒤 점유 캐시를 지우는지 검증한다.
 * - 어드민 회원별 예약 조회(`getMemberReservations`)의 정류소 결합과 graceful fallback 을 검증한다.
 */
@ExtendWith(MockKExtension::class)
@DisplayName("ReservationFacade 단위 테스트")
class ReservationFacadeTest {

    @MockK
    private lateinit var stopResolutionService: StopResolutionService

    @MockK
    private lateinit var reservationService: ReservationService

    @MockK(relaxUnitFun = true)
    private lateinit var stopHoldGate: StopHoldGate

    @InjectMockKs
    private lateinit var reservationFacade: ReservationFacade

    private val memberId = UUID.randomUUID()
    private val stopId = UUID.randomUUID()
    private val slotNo = StopSlot.DEFAULT_NO
    private val principal = mockk<MemberPrincipal>().also { every { it.id } returns memberId }

    private fun stopInfo(id: UUID) = StopInfo(id = id, stopId = "STOP-001", stopName = "강남역")

    private fun request(vararg stopIds: UUID) = CreateStopReservationRequest(
        stopId = linkedSetOf(*stopIds),
        consultationRequestedAt = OffsetDateTime.parse("2026-11-20T10:00:00+09:00"),
        desiredContractStartDate = null,
    )

    private fun pass(id: UUID) = StopHoldGate.Pass(StopHoldGate.keyOf(id, slotNo), "token-$id")

    /** DB 저장에 성공한 예약 엔티티 대역 */
    private fun savedReservation(holdExpiresAt: OffsetDateTime): ReservationEntity {
        val reservationId = UUID.randomUUID()
        return mockk<ReservationEntity>().also {
            every { it.id.value } returns reservationId
            every { it.holdExpiresAt } returns holdExpiresAt
        }
    }

    /** stop 정보가 아직 결합되지 않은(null) 원시 예약 row */
    private fun rawReservation(): MyReservationResponse =
        MyReservationResponse(
            reservationId = UUID.randomUUID(),
            stopId = stopId,
            stopNumber = null,
            stopName = null,
            status = ReservationStatus.PENDING,
            holdExpiresAt = null,
            consultationRequestedAt = OffsetDateTime.now(),
            desiredContractStartDate = null,
            createdAt = OffsetDateTime.now(),
        )

    @Test
    @DisplayName("게이트를 통과하면 정류소 확인 → DB 저장 → 점유 캐시 전환 순서로 예약한다")
    fun `예약 성공`() {
        val req = request(stopId)
        val holdExpiresAt = OffsetDateTime.parse("2026-11-27T10:00:00+09:00")
        val saved = savedReservation(holdExpiresAt)
        every { stopHoldGate.tryEnter(stopId, slotNo) } returns pass(stopId)
        every { stopResolutionService.resolveStop(stopId) } returns stopInfo(stopId)
        every { reservationService.createReservation(memberId, stopInfo(stopId), req, slotNo) } returns saved

        val result = reservationFacade.createReservation(principal, req)

        assertThat(result.successCount).isEqualTo(1)
        assertThat(result.failureCount).isZero()
        assertThat(result.results.single().success).isTrue()
        assertThat(result.results.single().reservationId).isEqualTo(saved.id.value)
        assertThat(result.results.single().holdExpiresAt).isEqualTo(holdExpiresAt)
        verifyOrder {
            stopHoldGate.tryEnter(stopId, slotNo)
            stopResolutionService.resolveStop(stopId)
            reservationService.createReservation(memberId, stopInfo(stopId), req, slotNo)
            stopHoldGate.confirm(pass(stopId))
        }
    }

    @Test
    @DisplayName("게이트에서 막히면 stop S2S 와 DB 를 호출하지 않고 선점됨으로 실패한다")
    fun `게이트 거절`() {
        every { stopHoldGate.tryEnter(stopId, slotNo) } returns null

        val result = reservationFacade.createReservation(principal, request(stopId))

        assertThat(result.successCount).isZero()
        assertThat(result.results.single().success).isFalse()
        assertThat(result.results.single().errorCode).isEqualTo(ReservationResult.STOP_ALREADY_HELD.code)
        verify(exactly = 0) { stopResolutionService.resolveStop(any()) }
        verify(exactly = 0) { reservationService.createReservation(any(), any(), any(), any()) }
    }

    @Test
    @DisplayName("3개 중 1개가 이미 선점돼 있어도 나머지 2개는 예약된다")
    fun `부분 성공`() {
        val first = UUID.randomUUID()
        val held = UUID.randomUUID()
        val third = UUID.randomUUID()
        val req = request(first, held, third)
        listOf(first, held, third).forEach {
            every { stopHoldGate.tryEnter(it, slotNo) } returns pass(it)
            every { stopResolutionService.resolveStop(it) } returns stopInfo(it)
        }
        every { reservationService.createReservation(memberId, stopInfo(first), req, slotNo) } returns
            savedReservation(OffsetDateTime.now())
        every { reservationService.createReservation(memberId, stopInfo(held), req, slotNo) } throws
            BusinessException(ReservationResult.STOP_ALREADY_HELD)
        every { reservationService.createReservation(memberId, stopInfo(third), req, slotNo) } returns
            savedReservation(OffsetDateTime.now())

        val result = reservationFacade.createReservation(principal, req)

        assertThat(result.successCount).isEqualTo(2)
        assertThat(result.failureCount).isEqualTo(1)
        assertThat(result.results.map { it.stopId }).containsExactly(first, held, third)
        assertThat(result.results.map { it.success }).containsExactly(true, false, true)
        assertThat(result.results[1].errorCode).isEqualTo(ReservationResult.STOP_ALREADY_HELD.code)
    }

    @Test
    @DisplayName("DB 가 이미 선점됨으로 판정하면 게이트 키를 풀지 않고 점유 캐시로 다시 건다")
    fun `DB 선점 충돌 시 점유 캐시 재설정`() {
        val req = request(stopId)
        every { stopHoldGate.tryEnter(stopId, slotNo) } returns pass(stopId)
        every { stopResolutionService.resolveStop(stopId) } returns stopInfo(stopId)
        every { reservationService.createReservation(memberId, stopInfo(stopId), req, slotNo) } throws
            BusinessException(ReservationResult.STOP_ALREADY_HELD)

        reservationFacade.createReservation(principal, req)

        verify(exactly = 1) { stopHoldGate.confirm(pass(stopId)) }
        verify(exactly = 0) { stopHoldGate.leave(any()) }
    }

    @Test
    @DisplayName("선점과 무관한 실패(없는 정류소)는 게이트를 풀어 다음 요청이 바로 시도할 수 있게 한다")
    fun `정류소 없음 실패 시 게이트 해제`() {
        every { stopHoldGate.tryEnter(stopId, slotNo) } returns pass(stopId)
        every { stopResolutionService.resolveStop(stopId) } throws StorageException(message = "요청한 정류소를 찾을 수 없습니다.")

        val result = reservationFacade.createReservation(principal, request(stopId))

        assertThat(result.results.single().success).isFalse()
        assertThat(result.results.single().message).isEqualTo("요청한 정류소를 찾을 수 없습니다.")
        verify(exactly = 1) { stopHoldGate.leave(pass(stopId)) }
        verify(exactly = 0) { stopHoldGate.confirm(any()) }
    }

    @Test
    @DisplayName("예기치 못한 오류도 해당 정류소만 실패로 기록하고 게이트를 푼다")
    fun `예기치 못한 오류 격리`() {
        every { stopHoldGate.tryEnter(stopId, slotNo) } returns pass(stopId)
        every { stopResolutionService.resolveStop(stopId) } throws IllegalStateException("연결 끊김")

        val result = reservationFacade.createReservation(principal, request(stopId))

        assertThat(result.results.single().success).isFalse()
        verify(exactly = 1) { stopHoldGate.leave(pass(stopId)) }
    }

    @Test
    @DisplayName("예약을 취소하면 풀린 광고면의 점유 캐시를 지운다")
    fun `취소 후 점유 캐시 삭제`() {
        val reservationId = UUID.randomUUID()
        val slot = SlotRef(stopId, slotNo)
        every { reservationService.cancelReservation(memberId, reservationId) } returns slot

        reservationFacade.cancelReservation(principal, reservationId)

        verifyOrder {
            reservationService.cancelReservation(memberId, reservationId)
            stopHoldGate.evict(slot)
        }
    }

    @Test
    @DisplayName("선점이 유지되는 상태 변경(상담 시작)은 점유 캐시를 건드리지 않는다")
    fun `상담 시작은 점유 캐시 유지`() {
        val reservationId = UUID.randomUUID()
        every { reservationService.updateReservationStatus(reservationId, ReservationStatus.CONSULTING, null) } returns null

        reservationFacade.updateReservationStatus(reservationId, ReservationStatus.CONSULTING, null)

        verify(exactly = 0) { stopHoldGate.evict(any()) }
    }

    @Test
    @DisplayName("만료 처리된 광고면마다 점유 캐시를 지우고 처리 건수를 돌려준다")
    fun `만료 후 점유 캐시 삭제`() {
        val slots = listOf(SlotRef(UUID.randomUUID(), slotNo), SlotRef(UUID.randomUUID(), slotNo))
        every { reservationService.expireOverdueHolds() } returns slots

        val expired = reservationFacade.expireOverdueHolds()

        assertThat(expired).isEqualTo(2)
        slots.forEach { verify(exactly = 1) { stopHoldGate.evict(it) } }
    }

    @Test
    @DisplayName("회원별 예약을 memberId 로 조회하고 정류소 정보를 결합한다")
    fun `정상 조회 및 정류소 결합`() {
        every { reservationService.getMyReservations(memberId, 0, 20) } returns
            PageResult(items = listOf(rawReservation()), totalCnt = 1, pageRows = 20, pageNum = 0)
        every { stopResolutionService.resolveStops(setOf(stopId)) } returns
            mapOf(stopId to StopInfo(id = stopId, stopId = "STOP-001", stopName = "강남역"))

        val result = reservationFacade.getMemberReservations(memberId, 0, 20)

        assertThat(result.items).hasSize(1)
        assertThat(result.items[0].stopNumber).isEqualTo("STOP-001")
        assertThat(result.items[0].stopName).isEqualTo("강남역")
        assertThat(result.totalCnt).isEqualTo(1L)
        // 본인이 아닌 대상 memberId 로 조회했는지 확인
        verify { reservationService.getMyReservations(memberId, 0, 20) }
    }

    @Test
    @DisplayName("stop 서비스 장애(빈 맵)면 stopNumber/stopName 은 null 로 fallback 된다")
    fun `정류소 조회 실패 fallback`() {
        every { reservationService.getMyReservations(memberId, 0, 20) } returns
            PageResult(items = listOf(rawReservation()), totalCnt = 1, pageRows = 20, pageNum = 0)
        every { stopResolutionService.resolveStops(setOf(stopId)) } returns emptyMap()

        val result = reservationFacade.getMemberReservations(memberId, 0, 20)

        assertThat(result.items).hasSize(1)
        assertThat(result.items[0].stopNumber).isNull()
        assertThat(result.items[0].stopName).isNull()
    }
}
