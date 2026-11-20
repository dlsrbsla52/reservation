package com.media.bus.iam.member.dto

import com.media.bus.contract.entity.member.MemberType
import com.media.bus.iam.member.entity.enumerated.MemberStatus
import java.time.OffsetDateTime

/** 회원 조건 검색 파라미터 */
data class MemberSearchCondition(
    val keyword: String? = null,

    val status: MemberStatus? = null,

    val type: MemberType? = null,

    val businessNumber: String? = null,

    val createdFrom: OffsetDateTime? = null,

    val createdTo: OffsetDateTime? = null,

    val page: Int = 0,

    val size: Int = 20,
)
