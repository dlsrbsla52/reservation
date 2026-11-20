package com.media.bus.reservation.contract.dto.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.util.*

/** 회원 정보의 reservation 내부 표현 (`MemberLookupPort` 응답) */
@JsonIgnoreProperties(ignoreUnknown = true)
data class MemberInfo(
    val id: UUID,
    val loginId: String,
    val email: String,
    val memberType: String,
    val status: String,
)
