package com.media.bus.app.security

import com.media.bus.app.web.BusAppContextTest
import com.media.bus.content.entity.InquiryTable
import com.media.bus.contract.entity.member.MemberType
import com.media.bus.contract.entity.member.Permission
import com.media.bus.contract.security.JwtProvider
import com.media.bus.contract.security.MemberPrincipal
import com.media.bus.contract.security.TokenAudience
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.*

/** 온라인 문의 관리 API 권한(조회 READ, 처리·삭제 WRITE)을 실제 요청으로 확인한다 */
@BusAppContextTest
@DisplayName("온라인 문의 관리 권한")
class ContentInquiryAuthorizationTest {

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Value("\${local.server.port}")
    private var port: Int = 0

    private val http = HttpClient.newHttpClient()

    private val missingId = UUID.randomUUID()

    @BeforeEach
    fun createInquiryTable() {
        TransactionTemplate(transactionManager).executeWithoutResult {
            TransactionManager.current().exec("CREATE SCHEMA IF NOT EXISTS content")
            SchemaUtils.create(InquiryTable)
        }
    }

    private fun adminToken(vararg permissions: Permission): String {
        val principal = MemberPrincipal(
            UUID.randomUUID(),
            "admin",
            "admin@example.com",
            MemberType.ADMIN_USER,
            true,
            permissions.toSet(),
            TokenAudience.ADMIN,
        )
        return jwtProvider.generateAccessToken(principal, permissions.map { it.name }.toSet(), TokenAudience.ADMIN)
    }

    private fun call(method: String, path: String, token: String, body: String? = null): HttpResponse<String> {
        val publisher = body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody()
        val request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$path"))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .method(method, publisher)
            .build()
        return http.send(request, HttpResponse.BodyHandlers.ofString())
    }

    @Test
    fun `READ 권한이 없는 관리자는 문의 목록을 볼 수 없다`() {
        assertThat(call("GET", "/api/v1/admin/content/inquiry", adminToken()).statusCode()).isEqualTo(403)
    }

    @Test
    fun `READ 권한만 있으면 목록은 보지만 처리 상태 변경과 삭제는 403이다`() {
        val token = adminToken(Permission.READ)

        assertThat(call("GET", "/api/v1/admin/content/inquiry", token).statusCode()).isEqualTo(200)
        assertThat(call("PUT", "/api/v1/admin/content/inquiry/$missingId/status", token, """{"status":"DONE"}""").statusCode())
            .isEqualTo(403)
        assertThat(call("DELETE", "/api/v1/admin/content/inquiry/$missingId", token).statusCode()).isEqualTo(403)
    }

    @Test
    fun `WRITE 권한이 있으면 처리 상태 변경과 삭제가 권한 검사를 통과해 서비스까지 간다`() {
        val token = adminToken(Permission.READ, Permission.WRITE)

        val status = call("PUT", "/api/v1/admin/content/inquiry/$missingId/status", token, """{"status":"DONE"}""")
        val delete = call("DELETE", "/api/v1/admin/content/inquiry/$missingId", token)

        assertThat(status.statusCode()).isEqualTo(404)
        assertThat(status.body()).contains("\"code\":\"W0004\"")
        assertThat(delete.statusCode()).isEqualTo(404)
        assertThat(delete.body()).contains("\"code\":\"W0004\"")
    }
}
