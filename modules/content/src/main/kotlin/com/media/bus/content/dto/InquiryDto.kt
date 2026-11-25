package com.media.bus.content.dto

import com.media.bus.content.entity.enums.InquiryStatus
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime
import java.util.*

/** 공개 온라인 문의 등록 요청 */
@Schema(description = "온라인 문의 등록 요청")
data class InquiryCreateRequest(
    @field:NotBlank @field:Size(max = 50)
    val name: String,
    @field:NotBlank @field:Pattern(regexp = "^[0-9-]{9,20}$")
    @param:Schema(example = "010-1234-5678")
    val phone: String,
    @field:NotBlank @field:Email @field:Size(max = 100)
    val email: String,
    @field:NotBlank @field:Size(max = 5000)
    val content: String,
    @param:Schema(description = "개인정보 수집 및 이용 동의. true여야 접수된다.")
    val privacyAgreed: Boolean? = null,
)

/** 문의 처리 상태 변경 명령 */
data class InquiryStatusCommand(
    @field:NotBlank
    val status: String,
    @field:Size(max = 2000)
    val memo: String? = null,
)

/** 온라인 문의 조회 모델 */
data class InquiryView(
    val id: UUID,
    val name: String,
    val phone: String,
    val email: String,
    val content: String,
    val status: InquiryStatus,
    val adminMemo: String?,
    val handledById: UUID?,
    val handledAt: OffsetDateTime?,
    val clientIp: String?,
    val createdAt: OffsetDateTime,
)
