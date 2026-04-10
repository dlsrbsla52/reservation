package com.media.bus.iam.member.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(description = "memberId를 통한 회원 조회 요청 객체")
data class FindMemberByMemberIdRequest(

    @field:NotBlank
    @param:Schema(name = "memberId", description = "memberId 회원 테이블 pk", example = "ac150006-9ce7-193d-819c-e759d6340000")
    val memberId: String,
)
