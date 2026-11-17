package com.media.bus.iam.port.dto.reservation

/**
 * ## 관리자 계약 목록 한 페이지 (`ReservationAdminPort` 응답)
 *
 * reservation 모듈의 `PageResult<AdminContractView>`를 iam 쪽 타입으로 옮긴 것이다. 모듈 경계상 reservation 클래스를 참조하지 않는다.
 * 필드가 nullable 인 것은 예전 HTTP 응답 역직렬화 시절의 흔적이다. 지금은 `app` 어댑터가 항상 채운다.
 */
data class AdminContractPage(
    val items: List<AdminContractView>?,
    val totalCnt: Long?,
    val pageRows: Int?,
    val pageNum: Int?,
)
