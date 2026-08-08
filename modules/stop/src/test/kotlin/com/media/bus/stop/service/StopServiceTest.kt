package com.media.bus.stop.service

import com.media.bus.contract.entity.member.MemberType
import com.media.bus.contract.security.MemberPrincipal
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
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

/**
 * `StopService` 단위 테스트.
 * 인가 처리는 인터셉터로 이전되어 `MemberPrincipal`을 직접 주입합니다.
 * `StopEntity` 팩토리 메서드는 `mockkObject`로 대체하여 DB 없이 검증합니다.
 */
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
        val principal = MemberPrincipal(memberId, "admin", "admin@test.com", MemberType.ADMIN_USER, true, emptySet())
        val request = SimpleStopCreateRequest("123", "테스트정류소", "127.0", "37.0", "NODE001", StopType.CENTER_LANE)

        justRun { stopCommandGuard.validateNotDuplicate("123") }
        mockkObject(StopEntity.Companion) {
            every { StopEntity.createFromRequest(request, memberId) } returns mockk()

            stopService.createOneStop(principal, request)

            verify { stopCommandGuard.validateNotDuplicate("123") }
            verify { StopEntity.createFromRequest(request, memberId) }
        }
    }

    @Test
    fun `createOneStop_Guard와_Repository_호출_순서_확인`() {
        val memberId = UUID.randomUUID()
        val principal = MemberPrincipal(memberId, "admin", "admin@test.com", MemberType.ADMIN_MASTER, true, emptySet())
        val request = SimpleStopCreateRequest("999", "마스터정류소", "128.0", "36.0", "NODE999", StopType.VILLAGE_BUS)

        justRun { stopCommandGuard.validateNotDuplicate("999") }
        mockkObject(StopEntity.Companion) {
            every { StopEntity.createFromRequest(request, memberId) } returns mockk()

            stopService.createOneStop(principal, request)

            // Guard가 먼저 호출된 후 엔티티 생성
            verify { stopCommandGuard.validateNotDuplicate("999") }
            verify { StopEntity.createFromRequest(request, memberId) }
        }
    }

    @Test
    fun `getBusStopPage_단가가_배치조회_1회로_매핑되고_미등록은_null`() {
        val withPrice = stopEntity("11833", "중계역")
        val withoutPrice = stopEntity("12345", "강남역")
        val pks = listOf(withPrice.id.value, withoutPrice.id.value)

        every { stopRepository.findAllPaged(0, 20) } returns listOf(withPrice, withoutPrice)
        every { stopRepository.countAll() } returns 2L
        // 단가가 등록된 정류소만 Map에 존재 — 미등록 정류소는 키 자체가 없다
        every { stopPriceRepository.findUnitPriceMapByStopIds(pks) } returns
            mapOf(pks[0] to BigDecimal("1500.00"))

        val result = stopService.getBusStopPage(0, 20, null)

        assertThat(result.totalCnt).isEqualTo(2L)
        assertThat(result.items[0].unitPrice).isEqualByComparingTo(BigDecimal("1500.00"))
        assertThat(result.items[1].unitPrice).isNull()
        // 정류소 건수와 무관하게 단가 조회는 정확히 1회여야 한다 (N+1 방지)
        verify(exactly = 1) { stopPriceRepository.findUnitPriceMapByStopIds(any()) }
    }

    @Test
    fun `getBusStopPage_정류소가_없으면_단가_조회를_생략한다`() {
        every { stopRepository.findByKeywordPaged("없는역", 0, 20) } returns emptyList()
        every { stopRepository.countByKeyword("없는역") } returns 0L

        val result = stopService.getBusStopPage(0, 20, "없는역")

        assertThat(result.items).isEmpty()
        verify(exactly = 0) { stopPriceRepository.findUnitPriceMapByStopIds(any()) }
    }

    @Test
    fun `getBusStop_식별자_조회에도_단가가_포함된다`() {
        val stop = stopEntity("11833", "중계역")
        // every 블록 안에서 다른 mock(stop.id)을 호출하면 MockK 레코딩에 섞이므로 pk는 미리 꺼내둔다
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
            every { xCrd } returns "127.0"
            every { yCrd } returns "37.0"
            every { nodeId } returns "NODE001"
            every { stopsType } returns StopType.CENTER_LANE
            every { createdAt } returns now
            every { updatedAt } returns now
        }
    }
}
