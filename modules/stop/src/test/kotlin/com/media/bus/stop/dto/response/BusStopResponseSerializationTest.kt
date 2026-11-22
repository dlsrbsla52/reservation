package com.media.bus.stop.dto.response

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

/** `BusStopResponse` 의 **직렬화 필드명 계약**을 고정하는 테스트. */
class BusStopResponseSerializationTest {

    private val objectMapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())

    @Test
    fun `좌표_필드는_xCrd_yCrd로_직렬화된다`() {
        val json = objectMapper.writeValueAsString(sample())

        assertThat(json).contains("\"xCrd\":\"127.0627647\"")
        assertThat(json).contains("\"yCrd\":\"37.6472686\"")
    }

    @Test
    fun `소문자_xcrd_필드는_더_이상_존재하지_않는다`() {
        val fields = objectMapper.readTree(objectMapper.writeValueAsString(sample()))
            .fieldNames().asSequence().toSet()

        assertThat(fields).contains("xCrd", "yCrd")
        assertThat(fields).doesNotContain("xcrd", "ycrd")
    }

    @Test
    fun `좌표는_지수_표기_없이_평문_숫자_문자열로_직렬화된다`() {
        val json = objectMapper.writeValueAsString(sample())

        assertThat(json).doesNotContain("E+")
        assertThat(json).doesNotContain("e+")
    }

    private fun sample() = BusStopResponse(
        id = UUID.randomUUID(),
        stopId = "11833",
        stopName = "중계역",
        xCrd = "127.0627647",
        yCrd = "37.6472686",
        nodeId = "110000669",
        stopsType = "ROADSIDE_TIMED",
        createdAt = OffsetDateTime.now(),
        updatedAt = OffsetDateTime.now(),
        unitPrice = BigDecimal("1500.00"),
    )
}
