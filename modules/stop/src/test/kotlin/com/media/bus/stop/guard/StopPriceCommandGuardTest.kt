package com.media.bus.stop.guard

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.stop.entity.StopEntity
import com.media.bus.stop.entity.StopPriceEntity
import com.media.bus.stop.repository.StopPriceRepository
import com.media.bus.stop.repository.StopRepository
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*

@ExtendWith(MockKExtension::class)
class StopPriceCommandGuardTest {

    @MockK
    lateinit var stopRepository: StopRepository

    @MockK
    lateinit var stopPriceRepository: StopPriceRepository

    @InjectMockKs
    lateinit var stopPriceCommandGuard: StopPriceCommandGuard

    @Test
    fun `validateStopExists_존재하는_정류소_예외없음`() {
        val stopId = UUID.randomUUID()
        every { stopRepository.findById(stopId) } returns mockk<StopEntity>()

        assertThatCode { stopPriceCommandGuard.validateStopExists(stopId) }
            .doesNotThrowAnyException()
    }

    @Test
    fun `validateStopExists_존재하지_않는_정류소_BusinessException_발생`() {
        val stopId = UUID.randomUUID()
        every { stopRepository.findById(stopId) } returns null

        assertThatThrownBy { stopPriceCommandGuard.validateStopExists(stopId) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("정류소를 찾을 수 없습니다.")
    }

    @Test
    fun `validateNotDuplicate_미등록_단가_예외없음`() {
        val stopId = UUID.randomUUID()
        every { stopPriceRepository.findByStopId(stopId) } returns null

        assertThatCode { stopPriceCommandGuard.validateNotDuplicate(stopId) }
            .doesNotThrowAnyException()
    }

    @Test
    fun `validateNotDuplicate_이미_등록된_단가_BusinessException_발생`() {
        val stopId = UUID.randomUUID()
        every { stopPriceRepository.findByStopId(stopId) } returns mockk<StopPriceEntity>()

        assertThatThrownBy { stopPriceCommandGuard.validateNotDuplicate(stopId) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("이미 등록된 정류소 단가입니다.")
    }

    @Test
    fun `validateExists_등록된_단가_있으면_엔티티_반환`() {
        val stopId = UUID.randomUUID()
        val entity = mockk<StopPriceEntity>()
        every { stopPriceRepository.findByStopId(stopId) } returns entity

        val result = stopPriceCommandGuard.validateExists(stopId)

        assertThatCode { result }.doesNotThrowAnyException()
    }

    @Test
    fun `validateExists_등록된_단가_없으면_BusinessException_발생`() {
        val stopId = UUID.randomUUID()
        every { stopPriceRepository.findByStopId(stopId) } returns null

        assertThatThrownBy { stopPriceCommandGuard.validateExists(stopId) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("등록된 정류소 단가가 없습니다.")
    }
}
