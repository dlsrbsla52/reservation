package com.media.bus.app.adapter

import com.media.bus.iam.member.service.MemberService
import com.media.bus.reservation.contract.dto.response.MemberInfo
import com.media.bus.reservation.contract.port.MemberLookupPort
import org.springframework.stereotype.Component
import java.util.*

/**
 * ## reservation → iam 회원 조회 어댑터
 *
 * 예전 `POST /api/v1/internal/iam/member/id`가 위임하던 `MemberService.findByMemberId`를 직접 부른다.
 * 회원이 없으면 iam의 `BusinessException`(USER_NOT_FOUND)이 그대로 전파된다(예전 4xx 전달과 같다).
 */
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
            // 예전 JSON 직렬화와 같은 값(enum name)
            memberType = member.memberType.name,
            status = member.status.name,
        )
    }
}
