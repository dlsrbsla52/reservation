package com.media.bus.iam.support

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest

class ClientIpResolverTest {
    @Test
    fun `전달 헤더를 위조해도 연결 IP만 사용한다`() {
        val request = MockHttpServletRequest().apply {
            remoteAddr = "192.0.2.1"
            addHeader("X-Forwarded-For", "198.51.100.1")
            addHeader("Forwarded", "for=198.51.100.1")
        }
        assertThat(ClientIpResolver.resolve(request)).isEqualTo("192.0.2.1")
        assertThat(ClientIpResolver.resolve(null)).isNull()
    }
}
