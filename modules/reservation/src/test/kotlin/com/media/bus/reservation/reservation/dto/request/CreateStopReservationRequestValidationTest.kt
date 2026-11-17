package com.media.bus.reservation.reservation.dto.request

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate

/**
 * ## 예약 요청 본문의 역직렬화 계약
 *
 * MVC의 Jackson(3, Kotlin 모듈 없음)은 Kotlin nullability를 직접 검사하지 않는다.
 * - `stopId` 원소: `["", ...]`·`[null, ...]`은 null 원소가 든 `Set<UUID>`가 되고, 막지 않으면 Facade에서 NPE(500)가 났다.
 * - `desiredContractStartDate`: 필수다(컬럼이 V001부터 NOT NULL). 예전에는 nullable 타입이라 빼면 INSERT가 제약 위반으로 500이 났다.
 *
 * 둘 다 역직렬화 단계에서 실패해야 한다. MVC에서는 `HttpMessageNotReadableException` → 400/`00260`이다.
 */
@DisplayName("CreateStopReservationRequest 역직렬화")
class CreateStopReservationRequestValidationTest {

    private val mapper = JsonMapper.builder().build()

    private fun body(firstElement: String, desired: String? = "\"2027-01-01\"") = buildString {
        append("""{"stopId":[$firstElement,"01a1e992-bc45-7599-8891-8f84bb94cd98"],"consultationRequestedAt":"2026-11-18T10:00:00Z"""")
        desired?.let { append(""","desiredContractStartDate":$it""") }
        append("}")
    }

    @ParameterizedTest
    @ValueSource(strings = ["\"\"", "null"])
    fun `stopId 에 빈 문자열이나 null 원소가 있으면 역직렬화가 실패한다`(element: String) {
        assertThrows<JacksonException> { mapper.readValue(body(element), CreateStopReservationRequest::class.java) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "null"])
    fun `희망 계약 시작일이 없거나 null 이면 역직렬화가 실패한다`(desired: String) {
        val json = body("\"01a1e992-bc45-7599-8891-8f84bb94cd99\"", desired.ifEmpty { null })

        assertThrows<JacksonException> { mapper.readValue(json, CreateStopReservationRequest::class.java) }
    }

    @Test
    fun `정상 요청은 그대로 역직렬화된다`() {
        val request = mapper.readValue(body("\"01a1e992-bc45-7599-8891-8f84bb94cd99\""), CreateStopReservationRequest::class.java)

        assertThat(request.stopId).hasSize(2)
        assertThat(request.desiredContractStartDate).isEqualTo(LocalDate.of(2027, 1, 1))
    }
}
