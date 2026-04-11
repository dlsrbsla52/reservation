package com.media.bus.iam.member.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "아이디 찾기를 위한 request")
data class FindMeRequest(

    @param:Schema(name = "memberName", description = "회원 이름", example = "퐝퐝규")
    val memberName: String,

    @param:Schema(name = "phoneNumber", description = "핸드폰 번호", example = "01012345678")
    val phoneNumber: String?,

    @param:Schema(name = "email", description = "이메일", example = "user@example.com")
    val email: String?
)