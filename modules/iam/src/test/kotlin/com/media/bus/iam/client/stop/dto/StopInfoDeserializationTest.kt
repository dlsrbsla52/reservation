package com.media.bus.iam.client.stop.dto

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.media.bus.iam.admin.dto.StopResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * `StopInfo` 의 **S2S 역직렬화 계약**을 고정하는 테스트.
 *
 * stop 모듈이 좌표 직렬화명을 `xcrd` → `xCrd` 로 고친 변경의 영향 범위를 지킨다.
 * 롤링 배포 중에는 구버전 stop 인스턴스가 `xcrd` 를, 신버전이 `xCrd` 를 보내므로
 * iam 은 **두 이름을 모두 받아들여야** 한다. 한쪽만 지원하면 배포 중 일부 요청이
 * 좌표 누락으로 실패한다.
 */
class StopInfoDeserializationTest {

    private val objectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)

    @Test
    fun `신규_직렬화명_xCrd를_역직렬화한다`() {
        val info = objectMapper.readValue(json("xCrd", "yCrd"), StopInfo::class.java)

        assertThat(info.xCrd).isEqualTo("127.0627647")
        assertThat(info.yCrd).isEqualTo("37.6472686")
    }

    @Test
    fun `구버전_직렬화명_xcrd도_역직렬화한다`() {
        val info = objectMapper.readValue(json("xcrd", "ycrd"), StopInfo::class.java)

        assertThat(info.xCrd).isEqualTo("127.0627647")
        assertThat(info.yCrd).isEqualTo("37.6472686")
    }

    @Test
    fun `어드민_응답_StopResponse는_xCrd_yCrd로_직렬화된다`() {
        val info = objectMapper.readValue(json("xCrd", "yCrd"), StopInfo::class.java)

        val fields = objectMapper.readTree(objectMapper.writeValueAsString(StopResponse.of(info)))
            .fieldNames().asSequence().toSet()

        assertThat(fields).contains("xCrd", "yCrd")
        assertThat(fields).doesNotContain("xcrd", "ycrd")
    }

    private fun json(xField: String, yField: String): String = """
        {
          "id": "018f1e2a-0000-7000-8000-000000000001",
          "stopId": "11833",
          "stopName": "중계역",
          "$xField": "127.0627647",
          "$yField": "37.6472686",
          "nodeId": "110000669",
          "stopsType": "ROADSIDE_TIMED",
          "createdAt": "2026-03-15T08:03:28.405709Z",
          "updatedAt": "2026-03-15T08:03:28.405709Z",
          "unitPrice": 1500.00
        }
    """.trimIndent()
}
