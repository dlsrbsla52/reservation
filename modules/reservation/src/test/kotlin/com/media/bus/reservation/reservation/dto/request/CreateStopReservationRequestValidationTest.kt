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

/** 예약 요청 본문의 역직렬화 계약 */
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
