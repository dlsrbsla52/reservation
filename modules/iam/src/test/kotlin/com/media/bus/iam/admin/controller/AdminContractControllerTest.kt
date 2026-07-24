package com.media.bus.iam.admin.controller

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.iam.admin.facade.AdminBusinessContractFacade
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.OffsetDateTime
import java.util.*

class AdminContractControllerTest {
    private val facade = mockk<AdminBusinessContractFacade>()
    private val controller = AdminContractController(facade)

    @Test
    fun `전체 계약 검색은 페이지와 크기를 허용 범위로 보정한다`() {
        every {
            facade.searchContracts(null, null, null, null, null, null, 0, 100)
        } returns PageResult(emptyList(), 0L, 100, 0)

        controller.searchContracts(
            memberId = null,
            stopId = null,
            status = null,
            keyword = null,
            contractStartFrom = null,
            contractStartTo = null,
            page = -1,
            size = 101,
        )

        verify(exactly = 1) {
            facade.searchContracts(null, null, null, null, null, null, 0, 100)
        }
    }

    @Test
    fun `계약 시작일 검색 범위가 역전되면 검증 예외가 발생한다`() {
        val from = OffsetDateTime.parse("2026-07-02T00:00:00+09:00")
        val to = OffsetDateTime.parse("2026-07-01T00:00:00+09:00")

        assertThrows<BusinessException> {
            controller.searchContracts(null, null, null, null, from, to, 0, 20)
        }
    }

    @Test
    fun `회원 계약 조회도 페이지와 크기를 허용 범위로 보정한다`() {
        val memberId = UUID.randomUUID()
        every { facade.getContractByMemberId(memberId, 0, 100) } returns mockk()

        controller.getMemberContracts(memberId, -1, 101)

        verify(exactly = 1) { facade.getContractByMemberId(memberId, 0, 100) }
    }
}
