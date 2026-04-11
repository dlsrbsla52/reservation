package com.media.bus.iam.admin.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** 회원 정지 요청 DTO */
@Schema(description = "회원 정지 요청 DTO")
data class SuspendMemberRequest(

    @param:Schema(description = "정지 사유", example = "이용약관 위반 — 부적절한 콘텐츠 게시")
    @field:NotBlank(message = "정지 사유를 입력해주세요.")
    @field:Size(max = 500, message = "정지 사유는 500자 이하로 입력해주세요.")
    val reason: String,
)
