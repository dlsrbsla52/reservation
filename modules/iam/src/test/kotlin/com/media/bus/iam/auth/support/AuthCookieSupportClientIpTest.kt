package com.media.bus.iam.auth.support

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest

/** 클라이언트 IP 추출 — `X-Forwarded-For` 위조 차단 */
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
