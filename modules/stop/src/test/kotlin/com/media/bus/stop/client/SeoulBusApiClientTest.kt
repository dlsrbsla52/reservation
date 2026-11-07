package com.media.bus.stop.client

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * 서울 공공 API 실제 연결 통합 테스트
 * - Spring 컨텍스트 없이 HTTP 클라이언트만 검증
 * - 실행 전 네트워크에서 openapi.seoul.go.kr:8088 접근 가능해야 함
 *
 * API 키는 코드에 두지 않는다. 환경변수 `SEOUL_API_KEY` → 프로젝트 루트 `.env` 순으로 찾고,
 * 둘 다 없으면 테스트를 **건너뛴다**(CI 등 키가 없는 환경에서 빌드를 깨지 않기 위함).
 */
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
