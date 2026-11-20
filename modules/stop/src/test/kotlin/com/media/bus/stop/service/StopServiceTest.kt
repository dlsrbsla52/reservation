package com.media.bus.stop.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.stop.dto.request.SimpleStopCreateRequest
import com.media.bus.stop.dto.request.StopSearchCriteria
import com.media.bus.stop.entity.StopEntity
import com.media.bus.stop.entity.StopTable
import com.media.bus.stop.entity.enums.StopType
import com.media.bus.stop.guard.StopCommandGuard
import com.media.bus.stop.repository.StopPriceRepository
import com.media.bus.stop.repository.StopRepository
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

/** `StopService` 단위 테스트. */
@ExtendWith(MockKExtension::class)
class StopServiceTest {

    @MockK
    lateinit var stopRepository: StopRepository

    @MockK
    lateinit var stopPriceRepository: StopPriceRepository

    @MockK
    lateinit var stopCommandGuard: StopCommandGuard

    @InjectMockKs
    lateinit var stopService: StopService

    @Test
    fun `createOneStop_Guard_호출_후_엔티티_생성_확인`() {
        val memberId = UUID.randomUUID()
        val request = SimpleStopCreateRequest("123", "테스트정류소", "127.0", "37.0", "NODE001", StopType.CENTER_LANE)

        justRun { stopCommandGuard.validateNotDuplicate("123") }
        mockkObject(StopEntity.Companion) {
            every { StopEntity.createFromRequest(request, memberId) } returns mockk()

            stopService.createOneStop(memberId, request)

            verify { stopCommandGuard.validateNotDuplicate("123") }
            verify { StopEntity.createFromRequest(request, memberId) }
        }
    }

    @Test
    fun `createOneStop_Guard와_Repository_호출_순서_확인`() {
        val memberId = UUID.randomUUID()
        val request = SimpleStopCreateRequest("999", "마스터정류소", "128.0", "36.0", "NODE999", StopType.VILLAGE_BUS)

        justRun { stopCommandGuard.validateNotDuplicate("999") }
        mockkObject(StopEntity.Companion) {
            every { StopEntity.createFromRequest(request, memberId) } returns mockk()

            stopService.createOneStop(memberId, request)

            verify { stopCommandGuard.validateNotDuplicate("999") }
            verify { StopEntity.createFromRequest(request, memberId) }
        }
    }

    @Test
    fun `getBusStopPage_단가가_배치조회_1회로_매핑되고_미등록은_null`() {
        val withPrice = stopEntity("11833", "중계역")
        val withoutPrice = stopEntity("12345", "강남역")
        val pks = listOf(withPrice.id.value, withoutPrice.id.value)

        every { stopRepository.findListPaged(null, null, 0, 20) } returns listOf(withPrice, withoutPrice)
        every { stopRepository.countList(null, null) } returns 2L
        every { stopPriceRepository.findUnitPriceMapByStopIds(pks) } returns
            mapOf(pks[0] to BigDecimal("1500.00"))

        val result = stopService.getBusStopPage(0, 20, null)

        assertThat(result.totalCnt).isEqualTo(2L)
        assertThat(result.items[0].unitPrice).isEqualByComparingTo(BigDecimal("1500.00"))
        assertThat(result.items[1].unitPrice).isNull()
        verify(exactly = 1) { stopPriceRepository.findUnitPriceMapByStopIds(any()) }
    }

    @Test
    fun `getBusStopPage_정류소가_없으면_단가_조회를_생략한다`() {
        every { stopRepository.findListPaged("없는역", null, 0, 20) } returns emptyList()
        every { stopRepository.countList("없는역", null) } returns 0L

        val result = stopService.getBusStopPage(0, 20, "없는역")

        assertThat(result.items).isEmpty()
        verify(exactly = 0) { stopPriceRepository.findUnitPriceMapByStopIds(any()) }
    }

    /** 유형 필터 문자열이 enum 으로 변환되어 Repository 까지 전달되는지 확인한다. */
    @Test
    fun `getBusStopPage_유형_필터는_enum으로_변환되어_전달된다`() {
        every { stopRepository.findListPaged(null, StopType.VILLAGE_BUS, 0, 20) } returns emptyList()
        every { stopRepository.countList(null, StopType.VILLAGE_BUS) } returns 0L

        val result = stopService.getBusStopPage(0, 20, null, "VILLAGE_BUS")

        assertThat(result.items).isEmpty()
        verify(exactly = 1) { stopRepository.findListPaged(null, StopType.VILLAGE_BUS, 0, 20) }
    }

    @Test
    fun `getBusStopPage_알_수_없는_유형은_400을_던진다`() {
        assertThatThrownBy { stopService.getBusStopPage(0, 20, null, "NOT_A_TYPE") }
            .isInstanceOf(BusinessException::class.java)
    }

    @Test
    fun `getBusStop_식별자_조회에도_단가가_포함된다`() {
        val stop = stopEntity("11833", "중계역")
        val pk = stop.id.value

        every { stopRepository.findByStopId("11833") } returns stop
        every { stopPriceRepository.findUnitPriceMapByStopIds(listOf(pk)) } returns
            mapOf(pk to BigDecimal("2000.00"))

        val result = stopService.getBusStop(StopSearchCriteria.of(null, "11833", null))

        assertThat(result).hasSize(1)
        assertThat(result[0].unitPrice).isEqualByComparingTo(BigDecimal("2000.00"))
    }

    /** DB 없이 `BusStopResponse.of` 변환에 필요한 필드만 채운 StopEntity 스텁을 만든다. */
    private fun stopEntity(stopId: String, stopName: String): StopEntity {
        val now = OffsetDateTime.now()
        return mockk<StopEntity> {
            every { id } returns EntityID(UUID.randomUUID(), StopTable)
            every { this@mockk.stopId } returns stopId
            every { this@mockk.stopName } returns stopName
            every { xCrd } returns BigDecimal("127.0000000")
            every { yCrd } returns BigDecimal("37.0000000")
            every { nodeId } returns "NODE001"
            every { stopsType } returns StopType.CENTER_LANE
            every { createdAt } returns now
            every { updatedAt } returns now
        }
    }

    @Test
    fun `정류소 이름이 비어 있으면 직접 기준 생성도 거부한다`() {
        listOf("", " ", "x".repeat(201)).forEach { name ->
            org.junit.jupiter.api.assertThrows<BusinessException> { StopSearchCriteria.ByStopName(name) }
            org.junit.jupiter.api.assertThrows<BusinessException> { StopSearchCriteria.of(null, null, name) }
        }
    }

    @Test
    fun `이름 검색이 100건을 넘으면 단가 조회 전에 명시적으로 거부한다`() {
        every { stopRepository.findByStopNameStartingWith("역") } returns List(101) { mockk() }
        org.junit.jupiter.api.assertThrows<BusinessException> {
            stopService.getBusStop(StopSearchCriteria.ByStopName("역"))
        }
        verify(exactly = 0) { stopPriceRepository.findUnitPriceMapByStopIds(any()) }
    }

    @Test
    fun `페이지 크기와 검색어 길이를 제공자 진입점에서 제한한다`() {
        listOf(0, 101, Int.MAX_VALUE).forEach { size ->
            org.junit.jupiter.api.assertThrows<BusinessException> { stopService.getBusStopPage(0, size, null) }
        }
        org.junit.jupiter.api.assertThrows<BusinessException> { stopService.getBusStopPage(-1, 20, null) }
        org.junit.jupiter.api.assertThrows<BusinessException> { stopService.getBusStopPage(0, 20, "x".repeat(201)) }
        verify(exactly = 0) { stopRepository.findListPaged(any(), any(), any(), any()) }
    }
}
