package com.media.bus.iam.member.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "회원 정보 수정 요청")
data class MemberModifyRequest (

    @param:Schema(description = "핸드폰 번호", example = "01012345678")
    val phoneNumber: String,

    @param:Schema(description = "email", example = "example@example.com")
    val email: String,
)