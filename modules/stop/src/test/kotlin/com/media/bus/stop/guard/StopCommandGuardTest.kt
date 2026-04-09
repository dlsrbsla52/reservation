package com.media.bus.stop.guard

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.stop.entity.StopEntity
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

/**
 * `StopCommandGuard` 단위 테스트.
 * - stopId가 이미 존재하면 → 409 CONFLICT (중복 방지)
 * - stopId가 존재하지 않으면 → 예외 없음 (신규 등록 허용)
 */
@ExtendWith(MockKExtension::class)
class StopCommandGuardTest {

    @MockK
    lateinit var stopRepository: StopRepository

    @InjectMockKs
    lateinit var stopCommandGuard: StopCommandGuard

    @Test
    fun `validateNotDuplicate_미등록_정류소_예외없음`() {
        every { stopRepository.findByStopId("999") } returns null

        assertThatCode { stopCommandGuard.validateNotDuplicate("999") }
            .doesNotThrowAnyException()
    }

    @Test
    fun `validateNotDuplicate_이미등록된_정류소_BusinessException_발생`() {
        every { stopRepository.findByStopId("123") } returns mockk<StopEntity>()

        assertThatThrownBy { stopCommandGuard.validateNotDuplicate("123") }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("이미 등록된 정류장입니다.")
    }
}
