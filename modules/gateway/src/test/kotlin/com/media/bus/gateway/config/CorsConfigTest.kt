package com.media.bus.gateway.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange

/**
 * 경로별 CORS origin 분리를 검증한다.
 * 관리자 경로가 일반 origin 을 허용하면, 일반 사이트 스크립트가 관리자 refresh 응답(토큰)을 읽을 수 있다.
 */
class CorsConfigTest {

    private val userOrigin = "https://www.mbus.kr"
    private val adminOrigin = "https://admin.mbus.kr"

    private fun originsFor(path: String, config: CorsConfig): List<String>? =
        config.corsConfigurationSource()
            .getCorsConfiguration(MockServerWebExchange.from(MockServerHttpRequest.get(path).build()))
            ?.allowedOrigins

    @Test
    fun `관리자 API 와 관리자 인증 경로는 관리자 origin 만 허용한다`() {
        val config = CorsConfig(userOrigin, adminOrigin)

        assertThat(originsFor("/api/v1/admin/stop", config)).containsExactly(adminOrigin)
        assertThat(originsFor("/api/v1/auth/admin/token/refresh", config)).containsExactly(adminOrigin)
    }

    @Test
    fun `그 외 경로는 일반과 관리자 origin 을 모두 허용한다`() {
        val config = CorsConfig(userOrigin, adminOrigin)

        assertThat(originsFor("/api/v1/member/me", config)).containsExactlyInAnyOrder(userOrigin, adminOrigin)
        assertThat(originsFor("/api/v1/auth/token/refresh", config)).containsExactlyInAnyOrder(userOrigin, adminOrigin)
    }

    @Test
    fun `관리자 origin 을 지정하지 않으면 일반 origin 으로 대체한다`() {
        val config = CorsConfig("http://localhost:3000", "")

        assertThat(originsFor("/api/v1/admin/stop", config)).containsExactly("http://localhost:3000")
        assertThat(originsFor("/api/v1/member/me", config)).containsExactly("http://localhost:3000")
    }
}
