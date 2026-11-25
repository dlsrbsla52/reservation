package com.media.bus.iam.port.dto.content

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime
import java.util.*

/** 관리자 이미지 업로드 URL 발급 요청 */
@Schema(description = "이미지 업로드 URL 발급 요청")
data class AdminImageUploadRequest(
    @field:NotBlank
    @param:Schema(description = "용도", allowableValues = ["PORTFOLIO", "NOTICE"])
    val purpose: String,
    @field:NotBlank
    @param:Schema(description = "MIME 형식", allowableValues = ["image/webp", "image/jpeg", "image/png"])
    val contentType: String,
    @field:Positive
    @param:Schema(description = "파일 크기(byte). 업로드할 파일 크기와 같아야 한다.")
    val contentLength: Long,
    @param:Schema(description = "썸네일 여부(기본 false). 키에 _thumb가 붙는다.")
    val thumbnail: Boolean? = null,
)

/** 관리자 이미지 업로드 URL */
@Schema(description = "발급한 업로드 URL. headers를 그대로 붙여 uploadUrl로 PUT 한 뒤 key로 등록한다.")
data class AdminImageUploadTicket(
    val key: String,
    val uploadUrl: String,
    val headers: Map<String, String>,
    val publicUrl: String,
    val expiresAt: OffsetDateTime,
)

/** 관리자 포트폴리오 */
@Schema(description = "관리자 포트폴리오 항목(숨김 포함)")
data class AdminPortfolio(
    val id: UUID,
    val title: String,
    val category: String,
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

/** 관리자 포트폴리오 등록·수정 요청 */
@Schema(description = "포트폴리오 등록·수정 요청")
data class AdminPortfolioRequest(
    @field:NotBlank @field:Size(max = 100)
    val title: String,
    @field:NotBlank
    @param:Schema(allowableValues = ["HOSPITAL", "ACADEMY", "FNB", "ETC"])
    val category: String,
    @field:Size(max = 500)
    val description: String? = null,
    @field:NotBlank @field:Size(max = 200)
    val imageKey: String,
    @field:NotBlank @field:Size(max = 200)
    val thumbnailKey: String,
    @param:Schema(description = "공개 노출 여부(기본 true)")
    val visible: Boolean? = null,
    @param:Schema(description = "기존 사이트 글 번호. 이관 스크립트만 쓴다. 이미 있으면 새로 만들지 않는다.")
    val legacyIdx: Int? = null,
)

/** 관리자 정렬 순서 변경 요청 */
@Schema(description = "정렬 순서 변경 요청. 화면에 보일 순서대로 전체 ID를 보낸다.")
data class AdminSortOrderRequest(
    @field:NotEmpty @field:Size(max = 2000)
    val ids: List<UUID>,
)

/** 관리자 공지사항 목록 항목 */
@Schema(description = "관리자 공지사항 목록 항목(숨김 포함)")
data class AdminNoticeSummary(
    val id: UUID,
    val title: String,
    val pinned: Boolean,
    val visible: Boolean,
    val viewCount: Int,
    val publishedAt: OffsetDateTime,
)

/** 관리자 공지사항 이미지 */
@Schema(description = "공지사항 첨부 이미지")
data class AdminNoticeImage(
    val key: String,
    val url: String,
)

/** 관리자 공지사항 상세 */
@Schema(description = "관리자 공지사항 상세")
data class AdminNotice(
    val id: UUID,
    val title: String,
    val body: String,
    val pinned: Boolean,
    val visible: Boolean,
    val viewCount: Int,
    val publishedAt: OffsetDateTime,
    val images: List<AdminNoticeImage>,
    val legacyIdx: Int?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

/** 관리자 공지사항 등록·수정 요청 */
@Schema(description = "공지사항 등록·수정 요청")
data class AdminNoticeRequest(
    @field:NotBlank @field:Size(max = 200)
    val title: String,
    @field:NotBlank @field:Size(max = 20000)
    val body: String,
    @param:Schema(description = "상단 고정(기본 false)")
    val pinned: Boolean? = null,
    @param:Schema(description = "공개 노출 여부(기본 true)")
    val visible: Boolean? = null,
    @param:Schema(description = "게시일. 비우면 등록 시각")
    val publishedAt: OffsetDateTime? = null,
    @param:Schema(description = "본문 아래 표시할 이미지 키(순서대로, 최대 10장). 생략하면 이미지 없음")
    val imageKeys: List<String>? = null,
    @param:Schema(description = "기존 사이트 글 번호. 이관 스크립트만 쓴다.")
    val legacyIdx: Int? = null,
    @field:PositiveOrZero
    @param:Schema(description = "기존 사이트 조회수. 이관 스크립트만 쓴다.")
    val legacyViewCount: Int? = null,
)

/** 관리자 FAQ */
@Schema(description = "관리자 FAQ 항목(숨김 포함)")
data class AdminFaq(
    val id: UUID,
    val category: String,
    val question: String,
    val answer: String,
    val sortOrder: Int,
    val visible: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

/** 관리자 FAQ 등록·수정 요청 */
@Schema(description = "FAQ 등록·수정 요청")
data class AdminFaqRequest(
    @field:NotBlank
    @param:Schema(allowableValues = ["MEDIA", "HOSPITAL"])
    val category: String,
    @field:NotBlank @field:Size(max = 300)
    val question: String,
    @field:NotBlank @field:Size(max = 10000)
    val answer: String,
    @param:Schema(description = "공개 노출 여부(기본 true)")
    val visible: Boolean? = null,
)

/** 관리자 온라인 문의 */
@Schema(description = "관리자 온라인 문의")
data class AdminInquiry(
    val id: UUID,
    val name: String,
    val phone: String,
    val email: String,
    val content: String,
    @param:Schema(allowableValues = ["RECEIVED", "DONE"])
    val status: String,
    val adminMemo: String?,
    val handledById: UUID?,
    val handledAt: OffsetDateTime?,
    val clientIp: String?,
    val createdAt: OffsetDateTime,
)

/** 관리자 문의 처리 상태 변경 요청 */
@Schema(description = "문의 처리 상태 변경 요청")
data class AdminInquiryStatusRequest(
    @field:NotBlank
    @param:Schema(allowableValues = ["RECEIVED", "DONE"])
    val status: String,
    @field:Size(max = 2000)
    @param:Schema(description = "처리 메모. 생략(null)하면 기존 메모를 유지하고, 빈 문자열이면 지운다.")
    val memo: String? = null,
)
