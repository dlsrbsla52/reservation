package com.media.bus.iam.admin.dto

import com.media.bus.iam.port.dto.stop.StopInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

/**
 * ## 관리자 정류소 API의 프런트 JSON 계약
 *
 * `xCrd` 같은 프로퍼티는 게터가 `getXCrd()`라 Jackson 기본 네이밍이 `xcrd`로 내보낸다.
 * 프런트는 `xCrd`/`yCrd`를 쓰므로 응답·요청 모두 이 이름이어야 한다. MVC와 같은 Jackson 3로 확인한다.
 *
 * (예전 `StopInfoDeserializationTest`·`SimpleStopCreateRequestSerializationTest`의 iam ↔ stop JSON 계약은
 * 모듈 간 호출이 직접 호출로 바뀌어 없어졌다. 프런트와의 계약만 남긴다.)
 */
class AdminStopDtoJsonTest {

    private val mapper = JsonMapper.builder().build()

    @Test
    fun `관리자 정류소 응답 StopResponse 는 xCrd·yCrd 로 직렬화된다`() {
        val now = OffsetDateTime.parse("2026-03-15T08:03:28Z")
        val info = StopInfo(
            UUID.randomUUID(), "11833", "중계역", "127.0627647", "37.6472686", "110000669", "ROADSIDE_TIMED",
            now, now, BigDecimal("1500.00"),
        )

        val fields = mapper.readTree(mapper.writeValueAsString(StopResponse.of(info))).propertyNames().toSet()

        assertThat(fields).contains("xCrd", "yCrd")
        assertThat(fields).doesNotContain("xcrd", "ycrd")
    }

    @Test
    fun `프런트가 보낸 xCrd·yCrd 를 정류소 등록 요청으로 역직렬화하고 포트 입력으로 옮긴다`() {
        val body = """{"stopId":"12345","stopName":"강남역","xCrd":"127.0627647","yCrd":"37.6472686","nodeId":"110000669","stopsType":"ROADSIDE_TIMED"}"""

        val request = mapper.readValue(body, CreateSimpleStopRequest::class.java)

        assertThat(request.xCrd).isEqualTo("127.0627647")
        assertThat(request.toPortRequest().yCrd).isEqualTo("37.6472686")
    }
}
