package com.media.bus.iam.member.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size

/**
 * ## 계정 비활성화 요청 DTO
 *
 * 사용자가 본인 계정을 일시 비활성화(`INACTIVE`)할 때 사용한다.
 * 사유는 감사 로그 용도로만 기록되며 선택 입력이다.
 */
@Schema(description = "계정 비활성화 요청 DTO")
data class DeactivateRequest(

    @param:Schema(description = "비활성화 사유 (선택)", example = "장기간 미사용 예정", nullable = true)
    @field:Size(max = 500, message = "사유는 500자 이하로 입력해주세요.")
    val reason: String? = null,
)
