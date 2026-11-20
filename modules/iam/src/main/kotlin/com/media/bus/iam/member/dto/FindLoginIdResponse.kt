package com.media.bus.iam.member.dto

import io.swagger.v3.oas.annotations.media.Schema

/** 아이디 찾기 응답 */
@Schema(description = "아이디 찾기 응답. 로그인 아이디만 담는다.")
data class FindLoginIdResponse(
    @param:Schema(name = "loginId", description = "로그인 아이디", example = "user123")
    val loginId: String,
)
