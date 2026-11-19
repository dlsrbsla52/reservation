package com.media.bus.app.security

import com.media.bus.common.logging.MdcLoggingFilter
import com.media.bus.contract.entity.member.MemberType
import com.media.bus.contract.security.ClaimCipher
import com.media.bus.contract.security.JwtProvider
import com.media.bus.contract.security.MemberPrincipal
import com.media.bus.contract.security.TokenAudience
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.mockk.mockk
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * ## 공개 포트 인증 필터 테스트
 *
 * 예전 gateway `JwtAuthenticationFilterTest`의 시나리오를 서블릿 필터로 옮기고,
 * 모듈러 모놀리스 전환 계획 2단계의 보안 완료 조건(헤더 위조 차단, 라우트 허용 목록)을 고정한다.
 */
@DisplayName("JwtAuthenticationFilter")
class JwtAuthenticationFilterTest {

    private val secret = "test-secret-key-for-unit-test-minimum-256bits-padding-here"
    private val claimKey = "dGVzdC1vbmx5LWR1bW15LWNsYWltLWtleS0zMmJ5dGU=" // 테스트 전용 더미 32바이트
    private val jwtProvider = JwtProvider(secret, claimKey, mockk<StringRedisTemplate>(relaxed = true))
    private val filter = JwtAuthenticationFilter(jwtProvider, exposeApiDocs = false, managementPort = { 8081 })

    /** 체인까지 도달한 요청. null 이면 필터가 요청을 끝냈다. */
    private var passed: HttpServletRequest? = null
    private var mdcMemberIdInChain: String? = null
    private val chain = FilterChain { req, _ ->
        passed = req as HttpServletRequest
        mdcMemberIdInChain = MDC.get(MdcLoggingFilter.MDC_MEMBER_ID)
    }

    @AfterEach
    fun clearMdc() = MDC.clear()

    private fun principal(type: MemberType, id: UUID = UUID.randomUUID()) =
        MemberPrincipal(id, "login", "a@b.c", type, true, emptySet())

    private fun token(type: MemberType, audience: TokenAudience, id: UUID = UUID.randomUUID()) =
        jwtProvider.generateAccessToken(principal(type, id), emptySet(), audience)

    private fun request(method: String, path: String, token: String? = null) =
        MockHttpServletRequest(method, path).apply {
            servletPath = path
            token?.let { addHeader("Authorization", "Bearer $it") }
        }

    private fun run(request: MockHttpServletRequest): MockHttpServletResponse {
        passed = null
        return MockHttpServletResponse().also { filter.doFilter(request, it, chain) }
    }

    private fun attachedPrincipal(): MemberPrincipal? =
        passed?.getAttribute(MemberPrincipal.REQUEST_ATTRIBUTE_KEY) as? MemberPrincipal

    @Nested
    @DisplayName("헤더 위조 차단")
    inner class ForgedHeaders {

        @Test
        fun `토큰 없이 X-User 헤더만 보내 보호 API 를 부르면 401`() {
            val req = request("GET", "/api/v1/member/me").apply {
                addHeader("X-User-Id", UUID.randomUUID().toString())
                addHeader("X-User-Role", "ROLE_ADMIN_MASTER")
            }

            val res = run(req)

            assertThat(res.status).isEqualTo(401)
            assertThat(passed).isNull()
        }

        @Test
        fun `본인 토큰과 다른 회원 X-User-Id 를 함께 보내면 토큰의 회원만 적용된다`() {
            val me = UUID.randomUUID()
            val other = UUID.randomUUID()
            val req = request("POST", "/api/v1/auth/logout", token(MemberType.MEMBER, TokenAudience.USER, me)).apply {
                addHeader("X-User-Id", other.toString())
            }

            run(req)

            assertThat(attachedPrincipal()?.id).isEqualTo(me)
            // 체인 아래에서는 위조 헤더가 보이지 않는다
            assertThat(passed?.getHeader("X-User-Id")).isNull()
            // ACCESS 로그용 MDC 에도 토큰의 회원 ID만 들어간다
            assertThat(mdcMemberIdInChain).isEqualTo(me.toString())
        }

        @Test
        fun `신뢰 헤더 6개를 대소문자와 무관하게 모두 숨긴다(X-Email-Verified 포함)`() {
            val req = request("POST", "/api/v1/auth/login").apply {
                MemberPrincipal.TRUSTED_HEADERS.forEach { addHeader(it.lowercase(), "forged") }
                addHeader("X-Request-ID", "keep-me")
            }

            run(req)

            val names = Collections.list(passed!!.headerNames).map { it.lowercase() }
            assertThat(names).doesNotContainAnyElementsOf(MemberPrincipal.TRUSTED_HEADERS.map { it.lowercase() })
            assertThat(passed!!.getHeader("x-email-verified")).isNull()
            assertThat(passed!!.getHeader("X-Request-ID")).isEqualTo("keep-me")
        }
    }

    @Nested
    @DisplayName("토큰 검증")
    inner class TokenValidation {

        @Test
        fun `토큰이 없으면 401`() {
            assertThat(run(request("GET", "/api/v1/member/me")).status).isEqualTo(401)
        }

        @Test
        fun `서명이 다른 토큰은 401`() {
            val forged = JwtProvider("another-secret-key-for-unit-test-minimum-256bits-xx", claimKey, mockk(relaxed = true))
                .generateAccessToken(principal(MemberType.MEMBER), emptySet(), TokenAudience.USER)
            assertThat(run(request("GET", "/api/v1/member/me", forged)).status).isEqualTo(401)
        }

        @Test
        fun `서명은 맞지만 클레임 암호화 키가 다른 토큰은 401`() {
            val otherKey = Base64.getEncoder().encodeToString(ByteArray(32) { 7 })
            val token = JwtProvider(secret, otherKey, mockk(relaxed = true))
                .generateAccessToken(principal(MemberType.MEMBER), emptySet(), TokenAudience.USER)
            assertThat(run(request("GET", "/api/v1/member/me", token)).status).isEqualTo(401)
        }

        @Test
        fun `클레임이 평문인 구버전 토큰은 서명이 맞아도 401`() {
            val legacy = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim(MemberPrincipal.CLAIM_MEMBER_TYPE, MemberType.ADMIN_MASTER.name)
                .signWith(Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8)))
                .compact()
            assertThat(run(request("GET", "/api/v1/member/me", legacy)).status).isEqualTo(401)
        }

        @Test
        fun `refresh 토큰을 액세스 토큰으로 쓰면 401`() {
            // 서명·sub 암호화는 정상이고 type=refresh 만 다른 토큰 — type 검사로 막히는지 본다
            val refresh = Jwts.builder()
                .subject(ClaimCipher(claimKey).encrypt(UUID.randomUUID().toString()))
                .claim("type", "refresh")
                .signWith(Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8)))
                .compact()
            assertThat(run(request("GET", "/api/v1/member/me", refresh)).status).isEqualTo(401)
        }

        @Test
        fun `일반 토큰은 일반 API 를 호출할 수 있다`() {
            run(request("GET", "/api/v1/member/me", token(MemberType.MEMBER, TokenAudience.USER)))
            assertThat(attachedPrincipal()).isNotNull
        }
    }

    @Nested
    @DisplayName("관리자 발급처 경계")
    inner class AdminAudience {

        @Test
        fun `일반 회원의 일반 사이트 토큰은 403`() {
            val res = run(request("GET", "/api/v1/admin/members", token(MemberType.MEMBER, TokenAudience.USER)))
            assertThat(res.status).isEqualTo(403)
        }

        @Test
        fun `관리자 계정이라도 aud=USER 토큰은 403`() {
            val res = run(request("GET", "/api/v1/admin/members", token(MemberType.ADMIN_MASTER, TokenAudience.USER)))
            assertThat(res.status).isEqualTo(403)
        }

        @Test
        fun `aud=ADMIN 이어도 관리자 카테고리가 아니면 403`() {
            val res = run(request("GET", "/api/v1/admin/members", token(MemberType.BUSINESS, TokenAudience.ADMIN)))
            assertThat(res.status).isEqualTo(403)
        }

        @Test
        fun `관리자 계정의 관리자 사이트 토큰은 통과한다`() {
            run(request("GET", "/api/v1/admin/members", token(MemberType.ADMIN_USER, TokenAudience.ADMIN)))
            assertThat(attachedPrincipal()?.memberType).isEqualTo(MemberType.ADMIN_USER)
        }

        @Test
        fun `admin 으로 시작하는 다른 경로는 관리자 경로로 보지 않는다(라우트 밖이면 404)`() {
            val res = run(request("GET", "/api/v1/adminx", token(MemberType.MEMBER, TokenAudience.USER)))
            assertThat(res.status).isEqualTo(404)
        }

        @Test
        fun `관리자 로그아웃은 토큰이 필요하다`() {
            assertThat(run(request("POST", "/api/v1/auth/admin/logout")).status).isEqualTo(401)
        }
    }

    @Nested
    @DisplayName("공개 경로와 라우트 허용 목록")
    inner class Routing {

        @Test
        fun `관리자 로그인과 토큰 갱신은 토큰 없이 통과한다`() {
            run(request("POST", "/api/v1/auth/admin/login"))
            assertThat(passed).isNotNull
            run(request("POST", "/api/v1/auth/admin/token/refresh"))
            assertThat(passed).isNotNull
        }

        @Test
        fun `공개 경로는 메서드와 경로가 정확히 같아야 한다(접두 비교 금지)`() {
            // 예전 startsWith 비교였다면 /api/v1/auth/login 의 접두라 공개됐을 경로
            assertThat(run(request("GET", "/api/v1/auth/login-history")).status).isEqualTo(401)
            // 메서드가 다르면 공개가 아니다
            assertThat(run(request("GET", "/api/v1/auth/login")).status).isEqualTo(401)
        }

        @Test
        fun `허용 prefix 밖의 경로는 토큰과 무관하게 404`() {
            val adminToken = token(MemberType.ADMIN_MASTER, TokenAudience.ADMIN)
            listOf("/actuator/loggers", "/actuator/health", "/error", "/swagger-ui.html", "/api-docs", "/api/v1/internal/stop")
                .forEach { path ->
                    assertThat(run(request("GET", path, adminToken)).status).describedAs(path).isEqualTo(404)
                    assertThat(passed).describedAs(path).isNull()
                }
        }

        @Test
        fun `문서 노출을 켜면 swagger 경로는 토큰 없이 통과한다`() {
            val docsFilter = JwtAuthenticationFilter(jwtProvider, exposeApiDocs = true, managementPort = { 8081 })
            passed = null
            docsFilter.doFilter(request("GET", "/api-docs.yaml"), MockHttpServletResponse(), chain)
            assertThat(passed).isNotNull
        }

        @Test
        fun `관리 포트로 들어온 요청은 필터가 건너뛴다`() {
            val req = request("GET", "/actuator/health").apply { localPort = 8081 }
            run(req)
            assertThat(passed).isNotNull
        }
    }
}
