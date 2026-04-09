package com.media.bus.iam.member.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(description = "이메일을 통한 회원 조회 요청 객체")
data class FindMemberByEmailRequest(

    @field:NotBlank
    @Schema(name = "email", description = "로그인 email", example = "vhkdrb52@gmail.com")
    val email: String,
)
