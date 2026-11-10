package com.media.bus.reservation.reservation.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime
import java.util.*

/**
 * ## 예약 생성 응답 DTO
 *
 * 여러 정류소를 한 번에 요청하면 정류소마다 독립적으로 선점을 시도한다.
 * 일부가 이미 선점됐어도 나머지는 예약되므로, 요청 전체의 성공/실패가 아니라 **정류소별 결과**를 돌려준다.
 * HTTP 상태는 전부 실패해도 200 이다 — 클라이언트는 `results` 의 `success` 로 판단한다.
 */
@Schema(description = "예약 생성 결과")
data class CreateReservationResponse(
    @param:Schema(description = "예약에 성공한 정류소 수")
    val successCount: Int,

    @param:Schema(description = "예약에 실패한 정류소 수")
    val failureCount: Int,

    @param:Schema(description = "정류소별 결과 (요청 순서)")
    val results: List<Item>,
) {

    @Schema(description = "정류소별 예약 결과")
    data class Item(
        @param:Schema(description = "정류소 PK (UUID)")
        val stopId: UUID,

        @param:Schema(description = "예약 성공 여부")
        val success: Boolean,

        @param:Schema(description = "생성된 예약 ID (성공 시)", nullable = true)
        val reservationId: UUID? = null,

        @param:Schema(description = "선점 만료 일시 (성공 시)", nullable = true)
        val holdExpiresAt: OffsetDateTime? = null,

        @param:Schema(description = "실패 결과 코드 (실패 시). 예: R0007 이미 선점됨", nullable = true)
        val errorCode: String? = null,

        @param:Schema(description = "실패 사유 (실패 시)", nullable = true)
        val message: String? = null,
    )

    companion object {
        fun of(results: List<Item>): CreateReservationResponse {
            val successCount = results.count { it.success }
            return CreateReservationResponse(
                successCount = successCount,
                failureCount = results.size - successCount,
                results = results,
            )
        }
    }
}
