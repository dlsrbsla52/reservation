package com.media.bus.stop.service

import com.media.bus.stop.entity.StopPriceEntity
import com.media.bus.stop.entity.StopPriceHistoryEntity
import com.media.bus.stop.entity.StopPriceTable
import com.media.bus.stop.entity.enums.StopPriceChangeType
import com.media.bus.stop.guard.StopPriceCommandGuard
import com.media.bus.stop.repository.StopPriceRepository
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
 * `StopPriceService` 단위 테스트.
 * `StopPriceEntity`/`StopPriceHistoryEntity` 팩토리 메서드는 `mockkObject`로 대체하여 DB 없이 검증합니다.
 */
@ExtendWith(MockKExtension::class)
class StopPriceServiceTest {

    @MockK
    lateinit var stopPriceRepository: StopPriceRepository

    @MockK
    lateinit var stopPriceCommandGuard: StopPriceCommandGuard

    @InjectMockKs
    lateinit var stopPriceService: StopPriceService

    @Test
    fun `createPrice_Guard_검증_후_엔티티와_이력이_생성된다`() {
        val stopId = UUID.randomUUID()
        val registeredById = UUID.randomUUID()
        val unitPrice = BigDecimal("1200000.00")
        val entity = mockk<StopPriceEntity> {
            every { id.value } returns UUID.randomUUID()
            every { this@mockk.stopId } returns stopId
            every { this@mockk.unitPrice } returns unitPrice
            every { this@mockk.registeredById } returns registeredById
            every { createdAt } returns java.time.OffsetDateTime.now()
            every { updatedAt } returns java.time.OffsetDateTime.now()
        }

        justRun { stopPriceCommandGuard.validateStopExists(stopId) }
        justRun { stopPriceCommandGuard.validateNotDuplicate(stopId) }

        mockkObject(StopPriceEntity.Companion) {
            mockkObject(StopPriceHistoryEntity.Companion) {
                every { StopPriceEntity.create(stopId, unitPrice, registeredById) } returns entity
                every {
                    StopPriceHistoryEntity.create(stopId, null, unitPrice, StopPriceChangeType.CREATE, registeredById)
                } returns mockk()

                val response = stopPriceService.createPrice(stopId, unitPrice, registeredById)

                assertThat(response.stopId).isEqualTo(stopId)
                assertThat(response.unitPrice).isEqualByComparingTo(unitPrice)
                verify { stopPriceCommandGuard.validateStopExists(stopId) }
                verify { stopPriceCommandGuard.validateNotDuplicate(stopId) }
                verify { StopPriceEntity.create(stopId, unitPrice, registeredById) }
                verify {
                    StopPriceHistoryEntity.create(stopId, null, unitPrice, StopPriceChangeType.CREATE, registeredById)
                }
            }
        }
    }

    @Test
    fun `updatePrice_이전_단가를_이력에_남기고_엔티티를_갱신한다`() {
        val stopId = UUID.randomUUID()
        val changedById = UUID.randomUUID()
        val previousPrice = BigDecimal("1000000.00")
        val newPrice = BigDecimal("1500000.00")
        val entity = mockk<StopPriceEntity>(relaxed = true) {
            every { id } returns EntityID(UUID.randomUUID(), StopPriceTable)
            every { unitPrice } returns previousPrice
            every { this@mockk.stopId } returns stopId
            every { registeredById } returns null
            every { createdAt } returns OffsetDateTime.now()
            every { updatedAt } returns OffsetDateTime.now()
        }

        every { stopPriceCommandGuard.validateExists(stopId) } returns entity
        justRun { entity.updatePrice(newPrice) }

        mockkObject(StopPriceHistoryEntity.Companion) {
            every {
                StopPriceHistoryEntity.create(stopId, previousPrice, newPrice, StopPriceChangeType.UPDATE, changedById)
            } returns mockk()

            stopPriceService.updatePrice(stopId, newPrice, changedById)

            verify { stopPriceCommandGuard.validateExists(stopId) }
            verify { entity.updatePrice(newPrice) }
            verify {
                StopPriceHistoryEntity.create(stopId, previousPrice, newPrice, StopPriceChangeType.UPDATE, changedById)
            }
        }
    }

    @Test
    fun `deletePrice_이전_단가를_이력에_남기고_엔티티를_삭제한다`() {
        val stopId = UUID.randomUUID()
        val changedById = UUID.randomUUID()
        val previousPrice = BigDecimal("1000000.00")
        val entity = mockk<StopPriceEntity>(relaxed = true) {
            every { unitPrice } returns previousPrice
            every { this@mockk.stopId } returns stopId
        }

        every { stopPriceCommandGuard.validateExists(stopId) } returns entity

        mockkObject(StopPriceHistoryEntity.Companion) {
            every {
                StopPriceHistoryEntity.create(stopId, previousPrice, null, StopPriceChangeType.DELETE, changedById)
            } returns mockk()

            stopPriceService.deletePrice(stopId, changedById)

            verify { stopPriceCommandGuard.validateExists(stopId) }
            verify {
                StopPriceHistoryEntity.create(stopId, previousPrice, null, StopPriceChangeType.DELETE, changedById)
            }
            verify { entity.delete() }
        }
    }

    @Test
    fun `getPrice_등록된_단가가_없으면_null_반환`() {
        val stopId = UUID.randomUUID()
        every { stopPriceRepository.findByStopId(stopId) } returns null

        val response = stopPriceService.getPrice(stopId)

        assertThat(response).isNull()
    }
}
