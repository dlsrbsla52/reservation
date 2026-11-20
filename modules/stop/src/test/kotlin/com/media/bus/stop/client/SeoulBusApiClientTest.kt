package com.media.bus.stop.client

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/** 서울 공공 API 실제 연결 통합 테스트(네트워크 필요). */
class SeoulBusApiClientTest {

    private lateinit var client: SeoulBusApiClient

    @BeforeEach
    fun setUp() {
        val apiKey = resolveApiKey()
        assumeTrue(!apiKey.isNullOrBlank(), "SEOUL_API_KEY 가 없어 실제 API 연결 테스트를 건너뜁니다.")
        client = SeoulBusApiClient(
            apiKey = apiKey!!,
            baseUrl = "http://openapi.seoul.go.kr:8088",
        )
    }

    /** 환경변수 우선, 없으면 Gradle 테스트 작업 디렉터리(모듈) 기준 상위의 루트 `.env` 에서 읽는다. */
    private fun resolveApiKey(): String? =
        System.getenv("SEOUL_API_KEY")?.takeIf { it.isNotBlank() }
            ?: listOf(File(".env"), File("../../.env"))
                .firstOrNull { it.isFile }
                ?.readLines()
                ?.firstOrNull { it.startsWith("SEOUL_API_KEY=") }
                ?.substringAfter("=")
                ?.trim()

    @Test
    fun `fetchTotalCount_실제_API_연결_확인`() {
        val totalCount = client.fetchTotalCount()

        assertThat(totalCount).isGreaterThan(0)
        println("총 정류소 수: $totalCount")
    }

    @Test
    fun `fetchStops_첫번째_페이지_조회`() {
        val stops = client.fetchStops(1, 10)

        assertThat(stops).isNotEmpty()
        assertThat(stops.size).isLessThanOrEqualTo(10)
        println("조회된 정류소: ${stops.size}개")
        stops.forEach { println("  - $it") }
    }
}
