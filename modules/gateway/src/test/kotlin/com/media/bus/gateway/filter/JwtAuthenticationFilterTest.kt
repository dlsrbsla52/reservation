package com.media.bus.gateway.filter

import com.media.bus.contract.entity.member.MemberType
import com.media.bus.contract.security.JwtProvider
import com.media.bus.contract.security.MemberPrincipal
import com.media.bus.contract.security.TokenAudience
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.route.Route
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.util.*

/**
 * gateway 의 **관리자 경계**를 검증한다.
 * 관리자 라우트는 관리자 계정 + 관리자 사이트(aud=ADMIN) 토큰만 하위 서비스로 보내야 한다.
 */
class JwtAuthenticationFilterTest {

    private val jwtProvider = JwtProvider(
        secret = "this-is-a-very-long-secret-key-for-hmac-sha256-algorithm-32chars",
        redisTemplate = mockk<StringRedisTemplate>(relaxed = true),
    ).let { real ->
        // 실제 서명·파싱은 그대로 쓰고, Redis 에 의존하는 S2S 토큰 발급만 고정한다.
        mockk<JwtProvider>().also {
            every { it.tryParseClaims(any()) } answers { real.tryParseClaims(firstArg()) }
            every { it.getPrincipalFromClaims(any()) } answers { real.getPrincipalFromClaims(firstArg()) }
            every { it.generateS2SToken() } returns "s2s-token"
        } to real
    }

    private val filter = JwtAuthenticationFilter(jwtProvider.first)

    private fun token(memberType: MemberType, audience: TokenAudience): String =
        jwtProvider.second.generateAccessToken(
            MemberPrincipal(UUID.randomUUID(), "login", "a@b.com", memberType, true, emptySet()),
            emptySet(),
            audience,
        )

    private val adminRoute: Route = Route.async()
        .id("iam-admin")
        .uri("http://localhost:8181")
        .predicate { true }
        .metadata(JwtAuthenticationFilter.REQUIRED_AUDIENCE_METADATA, "ADMIN")
        .build()

    private val plainRoute: Route = Route.async()
        .id("iam-service")
        .uri("http://localhost:8181")
        .predicate { true }
        .build()

    /** 필터를 실행하고 (하위 체인 호출 여부, 응답 상태, 체인에 전달된 exchange)를 돌려준다. */
    private fun run(
        path: String,
        bearer: String?,
        route: Route?,
        method: String = "GET",
    ): Triple<Boolean, HttpStatus?, ServerWebExchange?> {
        val request = MockServerHttpRequest.method(org.springframework.http.HttpMethod.valueOf(method), path)
            .apply { bearer?.let { header(HttpHeaders.AUTHORIZATION, "Bearer $it") } }
            .build()
        val exchange = MockServerWebExchange.from(request)
        route?.let { exchange.attributes[ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR] = it }

        var passed: ServerWebExchange? = null
        val chain = GatewayFilterChain { ex -> passed = ex; Mono.empty() }
        filter.filter(exchange, chain).block()

        return Triple(passed != null, exchange.response.statusCode as HttpStatus?, passed)
    }

    @Nested
    inner class 관리자_라우트 {

        @Test
        fun `일반 회원의 일반 사이트 토큰은 403`() {
            val (passed, status) = run("/api/v1/admin/stop", token(MemberType.MEMBER, TokenAudience.USER), adminRoute)

            assertThat(passed).isFalse()
            assertThat(status).isEqualTo(HttpStatus.FORBIDDEN)
        }

        @Test
        fun `관리자 계정이라도 aud=USER 토큰(구버전 포함)은 403`() {
            val (passed, status) = run("/api/v1/admin/stop", token(MemberType.ADMIN_MASTER, TokenAudience.USER), adminRoute)

            assertThat(passed).isFalse()
            assertThat(status).isEqualTo(HttpStatus.FORBIDDEN)
        }

        @Test
        fun `aud=ADMIN 이어도 관리자 카테고리가 아니면 403`() {
            val (passed, status) = run("/api/v1/admin/stop", token(MemberType.BUSINESS, TokenAudience.ADMIN), adminRoute)

            assertThat(passed).isFalse()
            assertThat(status).isEqualTo(HttpStatus.FORBIDDEN)
        }

        @Test
        fun `관리자 계정의 관리자 사이트 토큰은 통과하고 사용자 헤더가 주입된다`() {
            val (passed, _, forwarded) =
                run("/api/v1/admin/stop", token(MemberType.ADMIN_USER, TokenAudience.ADMIN), adminRoute)

            assertThat(passed).isTrue()
            assertThat(forwarded!!.request.headers.getFirst(MemberPrincipal.HEADER_USER_ROLE)).isEqualTo("ROLE_ADMIN_USER")
        }

        @Test
        fun `토큰이 없으면 401`() {
            val (passed, status) = run("/api/v1/admin/stop", null, adminRoute)

            assertThat(passed).isFalse()
            assertThat(status).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    @Nested
    inner class 경로_기준_안전망 {

        @Test
        fun `라우트 metadata 가 없어도 관리자 경로는 aud=ADMIN 을 요구한다`() {
            val (passed, status) = run("/api/v1/admin/members", token(MemberType.MEMBER, TokenAudience.USER), plainRoute)

            assertThat(passed).isFalse()
            assertThat(status).isEqualTo(HttpStatus.FORBIDDEN)
        }

        @Test
        fun `중복 슬래시로 관리자 경로 판정을 피할 수 없다`() {
            val (passed, status) = run("/api/v1//admin/members", token(MemberType.MEMBER, TokenAudience.USER), null)

            assertThat(passed).isFalse()
            assertThat(status).isEqualTo(HttpStatus.FORBIDDEN)
        }

        @Test
        fun `알 수 없는 required-audience 값은 ADMIN 으로 처리한다(fail-closed)`() {
            val typoRoute = Route.async()
                .id("stop-admin")
                .uri("http://localhost:8182")
                .predicate { true }
                .metadata(JwtAuthenticationFilter.REQUIRED_AUDIENCE_METADATA, "admin")
                .build()

            val (passed, status) = run("/api/v1/stop/admin/x", token(MemberType.MEMBER, TokenAudience.USER), typoRoute)

            assertThat(passed).isFalse()
            assertThat(status).isEqualTo(HttpStatus.FORBIDDEN)
        }

        @Test
        fun `admin 으로 시작하는 다른 경로는 관리자 경로로 보지 않는다`() {
            val (passed) = run("/api/v1/administrator", token(MemberType.MEMBER, TokenAudience.USER), plainRoute)

            assertThat(passed).isTrue()
        }
    }

    @Nested
    inner class 일반_라우트 {

        @Test
        fun `일반 토큰은 일반 API 를 호출할 수 있다`() {
            val (passed) = run("/api/v1/member/me", token(MemberType.MEMBER, TokenAudience.USER), plainRoute)

            assertThat(passed).isTrue()
        }

        @Test
        fun `관리자 토큰도 공통 API 를 호출할 수 있다`() {
            val (passed) = run("/api/v1/member/me", token(MemberType.ADMIN_MASTER, TokenAudience.ADMIN), plainRoute)

            assertThat(passed).isTrue()
        }

        @Test
        fun `관리자 로그인과 관리자 토큰 갱신은 토큰 없이 통과한다`() {
            assertThat(run("/api/v1/auth/admin/login", null, plainRoute, "POST").first).isTrue()
            assertThat(run("/api/v1/auth/admin/token/refresh", null, plainRoute, "POST").first).isTrue()
            assertThat(run("/api/v1/auth/admin/reactivate", null, plainRoute, "POST").first).isTrue()
        }

        @Test
        fun `계정 재활성화와 비밀번호 초기화는 토큰 없이 통과한다`() {
            listOf(
                "/api/v1/auth/reactivate",
                "/api/v1/auth/password-reset/request",
                "/api/v1/auth/password-reset/verify",
                "/api/v1/auth/password-reset/confirm",
            ).forEach { path ->
                assertThat(run(path, null, plainRoute, "POST").first).describedAs(path).isTrue()
            }
        }

        @Test
        fun `공개 경로에서 클라이언트가 넣은 사용자 헤더는 제거된다`() {
            val request = MockServerHttpRequest.post("/api/v1/auth/password-reset/request")
                .header(MemberPrincipal.HEADER_USER_ID, UUID.randomUUID().toString())
                .header(MemberPrincipal.HEADER_USER_ROLE, "ROLE_ADMIN_MASTER")
                .build()
            var forwarded: ServerWebExchange? = null

            filter.filter(MockServerWebExchange.from(request)) { ex -> forwarded = ex; Mono.empty() }.block()

            val headers = forwarded!!.request.headers
            assertThat(headers.containsHeader(MemberPrincipal.HEADER_USER_ID)).isFalse()
            assertThat(headers.containsHeader(MemberPrincipal.HEADER_USER_ROLE)).isFalse()
        }

        @Test
        fun `관리자 로그아웃은 토큰이 필요하다`() {
            val (passed, status) = run("/api/v1/auth/admin/logout", null, plainRoute, "POST")

            assertThat(passed).isFalse()
            assertThat(status).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }
}
