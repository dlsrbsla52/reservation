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

    @Test
    fun `공개 문의 접수는 4xx여도 요청과 응답 본문을 기록하지 않는다`() {
        capture { messages, complete ->
            val request = MockHttpServletRequest("POST", "/api/v1/content/inquiry").apply {
                contentType = "application/json"
                setContent("{\"name\":\"dummy-name\",\"phone\":\"01000000000\",\"content\":\"dummy-content\"}".toByteArray())
            }
            AccessLoggingFilter().doFilter(request, MockHttpServletResponse(), FilterChain { req, res ->
                req.inputStream.readAllBytes()
                (res as jakarta.servlet.http.HttpServletResponse).status = 400
                res.contentType = "application/json"
                res.writer.write("{\"message\":\"dummy-name 실패\"}")
            })
            assertThat(complete.await(2, TimeUnit.SECONDS)).isTrue()
            val access = messages.single { it.startsWith("ACCESS ") }
            assertThat(access).contains("-> 400").doesNotContain("dummy-name", "01000000000", "dummy-content", "req=", "res=")
        }
    }

    @Test
    fun `관리자 문의 경로는 하위 경로까지 응답 본문을 기록하지 않고 응답은 그대로 보낸다`() {
        capture { messages, complete ->
            val response = MockHttpServletResponse()
            val request = MockHttpServletRequest("GET", "/api/v1/admin/content/inquiry/0190aaaa-0000-7000-8000-000000000000")
            AccessLoggingFilter().doFilter(request, response, FilterChain { _, res ->
                (res as jakarta.servlet.http.HttpServletResponse).status = 404
                res.contentType = "application/json"
                res.writer.write("{\"name\":\"dummy-name\",\"email\":\"dummy@example.com\"}")
            })
            assertThat(complete.await(2, TimeUnit.SECONDS)).isTrue()
            assertThat(messages.joinToString()).doesNotContain("dummy-name", "dummy@example.com", "res=")
            assertThat(response.contentAsString).contains("dummy-name")
        }
    }

    @Test
    fun `문의와 이름이 비슷한 다른 경로는 기존처럼 에러 본문을 기록한다`() {
        capture { messages, complete ->
            val request = MockHttpServletRequest("GET", "/api/v1/content/inquiry-guide")
            AccessLoggingFilter().doFilter(request, MockHttpServletResponse(), FilterChain { _, res ->
                (res as jakarta.servlet.http.HttpServletResponse).status = 404
                res.contentType = "application/json"
                res.writer.write("{\"message\":\"없음\"}")
            })
            assertThat(complete.await(2, TimeUnit.SECONDS)).isTrue()
            assertThat(messages.single { it.startsWith("ACCESS ") }).contains("res=")
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
        try { block(messages, complete) } finally { logger.removeAppender(appender); appender.stop() }
    }
}
