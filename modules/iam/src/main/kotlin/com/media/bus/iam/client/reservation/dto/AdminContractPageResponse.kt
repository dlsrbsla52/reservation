package com.media.bus.iam.client.reservation.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * ## reservation 내부 API의 페이지 응답 역직렬화용 DTO
 *
 * 설계 의도:
 * - reservation 모듈의 `ApiResponse<PageResult<AdminContractView>>` 구조를 iam 내부 전용으로 매핑한다.
 * - 모듈 경계 원칙에 따라 reservation 모듈 클래스를 직접 참조하지 않는다.
 * - 알 수 없는 필드는 무시하여 reservation API 응답 변경에 유연하게 대응한다.
 *
 * 응답 JSON 형태:
 * ```
 * { "code": "...", "message": "...", "data": { "items": [...], "totalCnt": ..., "pageRows": ..., "pageNum": ... } }
 * ```
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AdminContractPageResponse(val data: AdminContractPage?) {

    /** reservation 측 `PageResult`에 매칭되는 페이지 데이터 구조 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AdminContractPage(
        val items: List<AdminContractView>?,
        val totalCnt: Long?,
        val pageRows: Int?,
        val pageNum: Int?,
    )
}
