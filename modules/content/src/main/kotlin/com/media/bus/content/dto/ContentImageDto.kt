package com.media.bus.content.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import java.time.OffsetDateTime

/** 이미지 업로드 URL 발급 명령 */
data class ImageUploadCommand(
    @field:NotBlank
    val purpose: String,
    @field:NotBlank
    val contentType: String,
    @field:Positive
    val contentLength: Long,
    val thumbnail: Boolean = false,
)

/** 발급한 업로드 URL. 브라우저는 `headers`를 붙여 `uploadUrl`로 PUT 한다. */
data class ImageUploadTicket(
    val key: String,
    val uploadUrl: String,
    val headers: Map<String, String>,
    val publicUrl: String,
    val expiresAt: OffsetDateTime,
)
