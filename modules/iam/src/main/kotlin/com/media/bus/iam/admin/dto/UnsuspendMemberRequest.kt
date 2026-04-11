package com.media.bus.iam.admin.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** 회원 정지 해제 요청 DTO */
@Schema(description = "회원 정지 해제 요청 DTO")
data class UnsuspendMemberRequest(

    @param:Schema(description = "정지 해제 사유", example = "경고 조치 완료 후 해제")
    @field:NotBlank(message = "정지 해제 사유를 입력해주세요.")
    @field:Size(max = 500, message = "정지 해제 사유는 500자 이하로 입력해주세요.")
    val reason: String,
)
