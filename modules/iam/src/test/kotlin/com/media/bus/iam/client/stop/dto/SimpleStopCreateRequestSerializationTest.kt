package com.media.bus.iam.client.stop.dto

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.media.bus.iam.admin.dto.CreateSimpleStopRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 정류소 단건 등록 요청의 **직렬화명 계약**을 고정하는 테스트.
 *
 * `xCrd` 는 게터가 `getXCrd()` 라서 Jackson 기본 네이밍이 `xcrd` 로 내보낸다.
 * stop 모듈은 `xCrd` 로 역직렬화하므로 이름이 어긋나면 좌표가 누락되어 400 이 된다.
 */
class SimpleStopCreateRequestSerializationTest {

    private val objectMapper = ObjectMapper().registerKotlinModule()

    @Test
    fun `iam이_stop으로_보내는_요청은_xCrd_yCrd로_직렬화된다`() {
        val request = SimpleStopCreateRequest("12345", "강남역", "127.0627647", "37.6472686", "110000669", "ROADSIDE_TIMED")

        val tree = objectMapper.readTree(objectMapper.writeValueAsString(request))

        assertThat(tree.has("xCrd")).isTrue()
        assertThat(tree.has("yCrd")).isTrue()
        assertThat(tree.has("xcrd")).isFalse()
    }

    @Test
    fun `프론트가_보내는_xCrd_yCrd를_어드민_요청으로_역직렬화하고_변환한다`() {
        val body = """{"stopId":"12345","stopName":"강남역","xCrd":"127.0627647","yCrd":"37.6472686","nodeId":"110000669","stopsType":"ROADSIDE_TIMED"}"""

        val request = objectMapper.readValue(body, CreateSimpleStopRequest::class.java)

        assertThat(request.xCrd).isEqualTo("127.0627647")
        assertThat(request.toClientRequest().yCrd).isEqualTo("37.6472686")
    }
}
