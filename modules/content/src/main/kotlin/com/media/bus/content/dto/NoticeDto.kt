package com.media.bus.content.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime
import java.util.*

/** 공지사항 등록·수정 명령. `legacyViewCount`는 기존 사이트 이관 때만 쓴다. */
data class NoticeCommand(
    @field:NotBlank @field:Size(max = 200)
    val title: String,
    @field:NotBlank @field:Size(max = 20000)
    val body: String,
    val pinned: Boolean = false,
    val visible: Boolean = true,
    val publishedAt: OffsetDateTime? = null,
    val imageKeys: List<String> = emptyList(),
    val legacyIdx: Int? = null,
    @field:PositiveOrZero
    val legacyViewCount: Int? = null,
)

/** 공지사항 목록 조회 모델 */
data class NoticeSummaryView(
    val id: UUID,
    val title: String,
    val pinned: Boolean,
    val visible: Boolean,
    val viewCount: Int,
    val publishedAt: OffsetDateTime,
)

/** 공지사항 이미지 조회 모델 */
data class ContentImageView(
    val key: String,
    val url: String,
)

/** 공지사항 상세 조회 모델 */
data class NoticeView(
    val id: UUID,
    val title: String,
    val body: String,
    val pinned: Boolean,
    val visible: Boolean,
    val viewCount: Int,
    val publishedAt: OffsetDateTime,
    val images: List<ContentImageView>,
    val legacyIdx: Int?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

/** 공개 공지사항 목록 항목 */
@Schema(description = "공개 공지사항 목록 항목")
data class NoticeSummaryResponse(
    val id: UUID,
    val title: String,
    val pinned: Boolean,
    val viewCount: Int,
    val publishedAt: OffsetDateTime,
) {
    companion object {
        fun of(view: NoticeSummaryView) = NoticeSummaryResponse(view.id, view.title, view.pinned, view.viewCount, view.publishedAt)
    }
}

/** 공개 공지사항 상세 */
@Schema(description = "공개 공지사항 상세. 이미지는 본문 아래에 순서대로 표시한다.")
data class NoticeDetailResponse(
    val id: UUID,
    val title: String,
    val body: String,
    val pinned: Boolean,
    val viewCount: Int,
    val publishedAt: OffsetDateTime,
    val imageUrls: List<String>,
) {
    companion object {
        fun of(view: NoticeView) = NoticeDetailResponse(
            id = view.id,
            title = view.title,
            body = view.body,
            pinned = view.pinned,
            viewCount = view.viewCount,
            publishedAt = view.publishedAt,
            imageUrls = view.images.map { it.url },
        )
    }
}
