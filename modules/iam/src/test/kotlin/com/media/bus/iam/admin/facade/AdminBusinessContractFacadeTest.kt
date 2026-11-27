package com.media.bus.iam.admin.facade

import com.media.bus.common.exceptions.NoAuthorizationException
import com.media.bus.contract.entity.member.MemberType
import com.media.bus.contract.entity.member.Permission
import com.media.bus.contract.security.MemberPrincipal
import com.media.bus.contract.security.TokenAudience
import com.media.bus.iam.admin.dto.CreateAdminContractRequest
import com.media.bus.iam.member.entity.enumerated.MemberStatus
import com.media.bus.iam.member.service.MemberService
import com.media.bus.iam.port.ReservationAdminPort
import com.media.bus.iam.port.dto.reservation.ContractView
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.OffsetDateTime
import java.util.*

class AdminBusinessContractFacadeTest {
    private val port = mockk<ReservationAdminPort>()
    private val memberService = mockk<MemberService>()
    private val facade = AdminBusinessContractFacade(port, memberService)
    private val admin = MemberPrincipal(
        UUID.randomUUID(), null, null, MemberType.ADMIN_USER, true, setOf(Permission.WRITE), TokenAudience.ADMIN,
    )
    private val request = CreateAdminContractRequest(
        stopId = UUID.randomUUID(),
        contractName = "광고 계약",
        totalAmount = null,
        payAmount = null,
        paymentCycle = "MONTHLY",
        paymentMethod = "CARD",
        memberId = UUID.randomUUID(),
        note = null,
        contractStartDate = OffsetDateTime.parse("2027-01-01T00:00:00+09:00"),
        contractEndDate = null,
        durationMonths = 1,
    )

    @Test
    fun `일반 관리자가 등록하면 로그인 계정이 담당자가 된다`() {
        every { port.createContract(any()) } returns contractView()

        facade.createContract(admin, request)

        verify(exactly = 1) {
            port.createContract(match { it.managerId == admin.id && it.memberId == request.memberId })
        }
    }

    @Test
    fun `일반 관리자는 다른 담당자를 지정할 수 없다`() {
        assertThrows<NoAuthorizationException> {
            facade.createContract(admin, request.copy(managerId = UUID.randomUUID()))
        }

        verify(exactly = 0) { port.createContract(any()) }
    }

    @Test
    fun `마스터는 활성 관리자를 담당자로 지정할 수 있다`() {
        val managerId = UUID.randomUUID()
        every { memberService.findByMemberId(managerId.toString()) } returns mockk {
            every { memberType } returns MemberType.ADMIN_USER
            every { status } returns MemberStatus.ACTIVE
        }
        every { port.createContract(any()) } returns contractView()

        facade.createContract(admin.copy(memberType = MemberType.ADMIN_MASTER), request.copy(managerId = managerId))

        verify(exactly = 1) { port.createContract(match { it.managerId == managerId }) }
    }

    private fun contractView() = ContractView(
        id = UUID.randomUUID(),
        stopId = request.stopId,
        stopNumber = null,
        stopName = null,
        memberId = request.memberId,
        contractName = request.contractName,
        status = "PENDING",
        contractStartDate = request.contractStartDate,
        contractEndDate = request.contractStartDate.plusMonths(1),
        createdAt = request.contractStartDate,
    )
}
