package com.media.bus.common.logging

import jakarta.servlet.FilterChain
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AccessLoggingFilterRequestTest {
    @Test
    fun `2KB에서 잘린 비밀번호는 실제 요청 로그에 남지 않고 응답은 보존된다`() {
        capture { messages, complete ->
            val request = MockHttpServletRequest("POST", "/api/v1/auth/login").apply {
                contentType = "application/json"
                setContent(("{\"newPassword\":\"" + "secret-fragment".repeat(300) + "\"}").toByteArray())
            }
            val response = MockHttpServletResponse()
            AccessLoggingFilter().doFilter(request, response, FilterChain { req, res ->
                req.inputStream.readAllBytes()
                (res as jakarta.servlet.http.HttpServletResponse).status = 400
                res.contentType = "application/json"
                res.writer.write("{\"message\":\"실패\"}")
            })
            assertThat(complete.await(2, TimeUnit.SECONDS)).isTrue()
            val access = messages.single { it.startsWith("ACCESS ") }
            assertThat(access).contains("newPassword", "***").doesNotContain("secret-fragment")
            assertThat(response.contentAsString).contains("실패")
        }
    }

    @Test
    fun `아이디 찾기 본문은 기록하지 않고 경로는 한번만 계산한다`() {
        capture { messages, complete ->
            var queryReads = 0
            val request = object : MockHttpServletRequest("POST", "/api/v1/member/find/me") {
                override fun getQueryString(): String {
                    queryReads++
                    return "token=secret-token"
                }
            }.apply {
                contentType = "application/json"
                setContent("{\"memberName\":\"dummy-name\",\"phoneNumber\":\"01000000000\"}".toByteArray())
            }
            AccessLoggingFilter().doFilter(request, MockHttpServletResponse(), FilterChain { req, res ->
                req.inputStream.readAllBytes()
                (res as jakarta.servlet.http.HttpServletResponse).status = 400
            })
            assertThat(complete.await(2, TimeUnit.SECONDS)).isTrue()
            assertThat(queryReads).isEqualTo(1)
            assertThat(messages.joinToString()).contains("token=***").doesNotContain("secret-token", "dummy-name", "01000000000")
        }
    }

    private fun capture(block: (ConcurrentLinkedQueue<String>, CountDownLatch) -> Unit) {
        val messages = ConcurrentLinkedQueue<String>()
        val complete = CountDownLatch(1)
        val appender = object : AbstractAppender("액세스로그검증", null, null, false, emptyArray()) {
            override fun append(event: LogEvent) {
                val message = event.message.formattedMessage
                messages.add(message)
                if (message.startsWith("ACCESS ")) complete.countDown()
            }
        }
        val logger = LogManager.getLogger(AccessLoggingFilter::class.java) as Logger
        appender.start()
        logger.addAppender(appender)
        try { block(messages, complete) }
        finally { logger.removeAppender(appender); appender.stop() }
    }
}
