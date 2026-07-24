package com.media.bus.reservation.contract.controller

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.reservation.contract.service.InternalContractFacade
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.OffsetDateTime
import java.util.*

class InternalContractControllerTest {
    private val facade = mockk<InternalContractFacade>()
    private val controller = InternalContractController(facade)

    @Test
    fun `회원 계약 조회는 페이지와 크기를 허용 범위로 보정한다`() {
        val memberId = UUID.randomUUID()
        every { facade.getContractsByMemberId(memberId, 0, 100) } returns
            PageResult(emptyList(), 0L, 100, 0)

        controller.getContractsByMember(memberId, -1, 101)

        verify(exactly = 1) { facade.getContractsByMemberId(memberId, 0, 100) }
    }

    @Test
    fun `계약 시작일 검색 범위가 역전되면 검증 예외가 발생한다`() {
        val from = OffsetDateTime.parse("2026-07-02T00:00:00+09:00")
        val to = OffsetDateTime.parse("2026-07-01T00:00:00+09:00")

        assertThrows<BusinessException> {
            controller.searchContracts(null, null, null, null, from, to, 0, 20)
        }
    }
}
