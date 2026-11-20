package com.media.bus.reservation.contract.port

import com.media.bus.reservation.contract.dto.response.MemberInfo
import java.util.*

/** 계약 흐름이 쓰는 회원 조회 포트 */
interface MemberLookupPort {

    /** 회원 UUID로 회원을 조회한다. */
    fun findMemberById(memberId: UUID): MemberInfo
}
