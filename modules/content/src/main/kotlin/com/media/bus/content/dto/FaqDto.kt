package com.media.bus.content.dto

import com.media.bus.content.entity.enums.FaqCategory
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime
import java.util.*

/** FAQ 등록·수정 명령 */
data class FaqCommand(
    @field:NotBlank
    val category: String,
    @field:NotBlank @field:Size(max = 300)
    val question: String,
    @field:NotBlank @field:Size(max = 10000)
    val answer: String,
    val visible: Boolean = true,
)

/** FAQ 조회 모델 */
data class FaqView(
    val id: UUID,
    val category: FaqCategory,
    val question: String,
    val answer: String,
    val sortOrder: Int,
    val visible: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

/** 공개 FAQ 응답 */
@Schema(description = "공개 FAQ 항목")
data class FaqResponse(
    val id: UUID,
    @param:Schema(description = "분류 코드", allowableValues = ["MEDIA", "HOSPITAL"])
    val category: String,
    val question: String,
    val answer: String,
) {
    companion object {
        fun of(view: FaqView) = FaqResponse(view.id, view.category.name, view.question, view.answer)
    }
}
