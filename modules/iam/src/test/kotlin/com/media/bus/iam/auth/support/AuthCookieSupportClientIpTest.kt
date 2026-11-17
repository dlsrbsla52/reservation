package com.media.bus.iam.auth.support

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest

/**
 * ## 클라이언트 IP 추출 — `X-Forwarded-For` 위조 차단
 *
 * 앱 8080이 요청을 직접 받으므로 클라이언트가 보낸 `X-Forwarded-For`를 믿으면
 * 세션 기기 정보·감사 로그의 IP가 위조된다. 커넥션의 `remoteAddr`만 써야 한다.
 */
@DisplayName("AuthCookieSupport.extractClientIp")
class AuthCookieSupportClientIpTest {

    private val support = AuthCookieSupport(cookieSecure = false)

    @Test
    fun `X-Forwarded-For 가 있어도 remoteAddr 를 쓴다`() {
        val request = MockHttpServletRequest().apply {
            remoteAddr = "203.0.113.10"
            addHeader("X-Forwarded-For", "1.2.3.4, 10.0.0.1")
        }

        assertThat(support.extractClientIp(request)).isEqualTo("203.0.113.10")
    }

    @Test
    fun `헤더가 없으면 remoteAddr 를 쓴다`() {
        val request = MockHttpServletRequest().apply { remoteAddr = "198.51.100.7" }

        assertThat(support.extractClientIp(request)).isEqualTo("198.51.100.7")
    }
}
