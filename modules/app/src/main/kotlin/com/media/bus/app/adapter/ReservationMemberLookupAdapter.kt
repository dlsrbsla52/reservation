package com.media.bus.app.adapter

import com.media.bus.iam.member.service.MemberService
import com.media.bus.reservation.contract.dto.response.MemberInfo
import com.media.bus.reservation.contract.port.MemberLookupPort
import org.springframework.stereotype.Component
import java.util.*

/** reservation → iam 회원 조회 어댑터 */
@Component
class ReservationMemberLookupAdapter(
    private val memberService: MemberService,
) : MemberLookupPort {

    override fun findMemberById(memberId: UUID): MemberInfo = callProvider {
        val member = memberService.findByMemberId(memberId.toString())
        MemberInfo(
            id = member.id,
            loginId = member.loginId,
            email = member.email,
            memberType = member.memberType.name,
            status = member.status.name,
        )
    }
}
