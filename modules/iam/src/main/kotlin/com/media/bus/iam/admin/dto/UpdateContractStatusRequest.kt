package com.media.bus.iam.admin.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UpdateContractStatusRequest(
    @field:NotBlank
    @param:Schema(allowableValues = ["ACTIVE", "CANCELLED", "EXPIRED"], example = "ACTIVE")
    val status: String,
    @field:Size(max = 500)
    @param:Schema(description = "취소할 때만 지정할 수 있는 사유")
    val cancelReason: String?,
)
