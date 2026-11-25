package com.media.bus.content.dto

import com.media.bus.content.entity.enums.PortfolioCategory
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime
import java.util.*

/** 포트폴리오 등록·수정 명령 */
data class PortfolioCommand(
    @field:NotBlank @field:Size(max = 100)
    val title: String,
    @field:NotBlank
    val category: String,
    @field:Size(max = 500)
    val description: String? = null,
    @field:NotBlank @field:Size(max = 200)
    val imageKey: String,
    @field:NotBlank @field:Size(max = 200)
    val thumbnailKey: String,
    val visible: Boolean = true,
    val legacyIdx: Int? = null,
)

/** 포트폴리오 조회 모델 */
data class PortfolioView(
    val id: UUID,
    val title: String,
    val category: PortfolioCategory,
    val description: String?,
    val imageKey: String,
    val thumbnailKey: String,
    val imageUrl: String,
    val thumbnailUrl: String,
    val sortOrder: Int,
    val visible: Boolean,
    val legacyIdx: Int?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

/** 공개 포트폴리오 응답 */
@Schema(description = "공개 포트폴리오 항목")
data class PortfolioResponse(
    val id: UUID,
    val title: String,
    @param:Schema(description = "분류 코드", allowableValues = ["HOSPITAL", "ACADEMY", "FNB", "ETC"])
    val category: String,
    val description: String?,
    val imageUrl: String,
    val thumbnailUrl: String,
) {
    companion object {
        fun of(view: PortfolioView) = PortfolioResponse(
            id = view.id,
            title = view.title,
            category = view.category.name,
            description = view.description,
            imageUrl = view.imageUrl,
            thumbnailUrl = view.thumbnailUrl,
        )
    }
}
