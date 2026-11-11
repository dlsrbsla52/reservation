package com.media.bus.common.client

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.exceptions.ServiceException
import com.media.bus.common.result.type.CommonResult
import com.media.bus.common.security.TokenProvider
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.net.InetSocketAddress

/**
 * S2S 하위 서비스 4xx 의 **상태 전달 계약**.
 * 4xx 가 500 으로 바뀌면 관리자는 중복·검증 실패를 서버 장애로 오인하고,
 * 401/403 을 그대로 넘기면 프론트가 사용자 토큰 문제로 오인해 로그아웃한다.
 */
class S2SRestClientFactoryErrorTest {

    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply { start() }
    private val baseUrl = "http://127.0.0.1:${server.address.port}"
    private val client = S2SRestClientFactory(object : TokenProvider {
        override fun generateS2SToken() = "s2s"
    }).create(baseUrl)

    @AfterEach
    fun tearDown() = server.stop(0)

    private fun respond(status: Int, body: String) {
        server.createContext("/x") { ex ->
            val bytes = body.toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(status, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
    }

    private fun call() = client.get().uri("/x").retrieve().body(String::class.java)

    @Test
    fun `409 는 같은 상태와 하위 메시지의 BusinessException 으로 전달된다`() {
        respond(409, """{"code":"00200","message":"정류소 일괄 등록이 이미 진행 중입니다."}""")

        assertThatThrownBy { call() }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage("정류소 일괄 등록이 이미 진행 중입니다.")
            .extracting("httpStatus").isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    fun `하위 서비스의 결과 코드를 그대로 전달한다`() {
        respond(409, """{"code":"R0010","message":"선점 기간을 연장할 수 없는 예약입니다."}""")

        assertThatThrownBy { call() }
            .isInstanceOfSatisfying(BusinessException::class.java) {
                assertThat(it.result.code).isEqualTo("R0010")
                assertThat(it.result.httpStatus()).isEqualTo(HttpStatus.CONFLICT)
                assertThat(it.httpStatus).isEqualTo(HttpStatus.CONFLICT)
            }
    }

    @Test
    fun `본문이 JSON 이 아니어도 상태는 유지되고 코드는 공통 실패 코드다`() {
        respond(400, "bad request")

        assertThatThrownBy { call() }
            .isInstanceOfSatisfying(BusinessException::class.java) {
                assertThat(it.httpStatus).isEqualTo(HttpStatus.BAD_REQUEST)
                assertThat(it.result).isEqualTo(CommonResult.FAIL)
            }
    }

    @Test
    fun `하위 서비스의 401 은 사용자 인증 오류가 아니라 ServiceException 이다`() {
        respond(401, """{"message":"S2S 토큰 오류"}""")

        assertThatThrownBy { call() }.isInstanceOf(ServiceException::class.java)
    }

    @Test
    fun `2xx 는 그대로 통과한다`() {
        respond(200, """{"ok":true}""")

        assertThat(call()).contains("ok")
    }
}
