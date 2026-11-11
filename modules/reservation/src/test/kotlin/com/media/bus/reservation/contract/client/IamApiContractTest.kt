package com.media.bus.reservation.contract.client

import com.media.bus.common.client.S2SRestClientFactory
import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.security.TokenProvider
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.net.InetSocketAddress
import java.util.*

/**
 * `IamApi`가 iam `MemberInternalController`와 같은 **메서드·경로·본문**으로 호출하는지 고정한다.
 *
 * `@HttpExchange` 선언은 상대 서비스와 어긋나도 컴파일되므로(과거 존재하지 않는 `GET .../{memberId}`를
 * 호출해 회원 계약 생성이 실패했다), 실제 HTTP 요청을 받아 확인한다.
 */
class IamApiContractTest {

    private data class Received(val method: String, val path: String, val serviceToken: String?, val body: String)

    private val received = mutableListOf<Received>()
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply { start() }
    private val client = IamServiceClient(
        S2SRestClientFactory(object : TokenProvider {
            override fun generateS2SToken() = "dummy-s2s-token"
        }).createProxy("http://127.0.0.1:${server.address.port}", IamApi::class.java),
    )

    @AfterEach
    fun tearDown() = server.stop(0)

    private fun respond(status: Int, body: String) {
        server.createContext("/") { ex ->
            received += Received(
                method = ex.requestMethod,
                path = ex.requestURI.path,
                serviceToken = ex.requestHeaders.getFirst("X-Service-Token"),
                body = ex.requestBody.readBytes().decodeToString(),
            )
            val bytes = body.toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(status, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
    }

    @Test
    fun `회원 조회는 internal 접두 경로에 POST 로 memberId 를 본문에 담아 보낸다`() {
        val memberId = UUID.fromString("018f1e2a-0000-7000-8000-000000000001")
        respond(
            200,
            """{"code":"00000","message":"ok","data":{"id":"$memberId","loginId":"dummy","email":"dummy@example.com",
               "phoneNumber":"010-0000-0000","memberType":"MEMBER","status":"ACTIVE","businessNumber":null}}""",
        )

        val member = client.findMemberById(memberId)

        assertThat(member?.id).isEqualTo(memberId)
        assertThat(member?.memberType).isEqualTo("MEMBER")
        val request = received.single()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/api/v1/internal/iam/member/id")
        assertThat(request.serviceToken).isEqualTo("dummy-s2s-token")
        assertThat(request.body).contains("\"memberId\":\"$memberId\"")
    }

    @Test
    fun `회원이 없으면 iam 의 상태와 코드가 BusinessException 으로 전달된다`() {
        respond(400, """{"code":"00104","message":"사용자를 찾을 수 없습니다."}""")

        assertThatThrownBy { client.findMemberById(UUID.randomUUID()) }
            .isInstanceOfSatisfying(BusinessException::class.java) {
                assertThat(it.httpStatus).isEqualTo(HttpStatus.BAD_REQUEST)
                assertThat(it.result.code).isEqualTo("00104")
            }
    }
}
