package com.media.bus.app

import com.media.bus.app.adapter.IamReservationAdminAdapter
import com.media.bus.app.adapter.IamStopAdminAdapter
import com.media.bus.app.adapter.ReservationMemberLookupAdapter
import com.media.bus.app.adapter.ReservationStopLookupAdapter
import com.media.bus.app.security.ApiPathPolicy
import com.media.bus.app.security.JwtAuthenticationFilter
import com.media.bus.contract.entity.member.MemberType
import com.media.bus.contract.security.JwtProvider
import com.media.bus.contract.security.MemberPrincipal
import com.media.bus.contract.security.TokenAudience
import com.media.bus.iam.port.ReservationAdminPort
import com.media.bus.iam.port.StopAdminPort
import com.media.bus.reservation.contract.port.MemberLookupPort
import com.media.bus.reservation.reservation.port.StopLookupPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.*

/**
 * ## 앱 전체 컨텍스트 기동과 공개 표면
 *
 * 다른 `app` 테스트는 단위 테스트이거나 리플렉션 스캔이라, 아래 문제는 실제로 띄워야만 드러났다. 이 테스트가 CI에서 잡는다.
 * - 빈 이름 충돌(`allow-bean-definition-overriding: false`), 포트 구현 누락·중복
 * - 공개 포트의 보안 체인 누락. `common`의 permitAll 기본 체인을 지웠으므로 `SecurityConfig`가 빠지면 이 테스트가 실패한다
 * - 실제 핸들러 매핑의 internal 경로, 라우트 허용 목록 밖 핸들러
 * - 공개 8080 / 관리 8081 포트 정책
 *
 * 외부 의존 없이 돈다: H2 메모리 DB(Liquibase 끔), Redis 는 접속하지 않는 포트(연결은 지연 생성이라 기동에 필요 없다).
 * 프로젝트 루트 `.env`가 있으면 기본 설정이 읽지만, 아래 속성이 우선한다.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "management.server.port=0",
        "spring.datasource.url=jdbc:h2:mem:bus-context-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.liquibase.enabled=false",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=1",
        "management.health.redis.enabled=false",
        "reservation.hold.expiry-enabled=false",
        "cookie.secure=false",
        "jwt.secret=context-test-only-secret-key-at-least-32-bytes-long",
        "jwt.claim-encryption-key=dGVzdC1vbmx5LWR1bW15LWNsYWltLWtleS0zMmJ5dGU=",
        "cors.allowed-origin=http://localhost:3000",
        "cors.admin-allowed-origin=http://admin.localhost:3001",
        "seoul.api.key=dummy",
        "app.security.expose-api-docs=false",
    ],
)
@DisplayName("앱 전체 컨텍스트")
class BusApplicationContextTest {

    @Autowired
    private lateinit var context: ApplicationContext

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private lateinit var handlerMapping: RequestMappingHandlerMapping

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @Value("\${local.server.port}")
    private var publicPort: Int = 0

    @Value("\${local.management.port}")
    private var managementPort: Int = 0

    private val http = HttpClient.newHttpClient()

    private fun get(port: Int, path: String, token: String? = null): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$path")).GET()
        token?.let { request.header("Authorization", "Bearer $it") }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }

    @Test
    fun `공개 포트 보안 체인은 하나이고 인증 필터가 들어 있다`() {
        val chains = context.getBeansOfType(SecurityFilterChain::class.java).values

        assertThat(chains).hasSize(1)
        assertThat(chains.single().filters).anyMatch { it is JwtAuthenticationFilter }
    }

    @Test
    fun `포트마다 app 어댑터가 정확히 하나 연결된다`() {
        // getBean(Class) 는 구현이 0개나 2개 이상이면 예외를 던진다
        assertThat(context.getBean(StopLookupPort::class.java)).isInstanceOf(ReservationStopLookupAdapter::class.java)
        assertThat(context.getBean(MemberLookupPort::class.java)).isInstanceOf(ReservationMemberLookupAdapter::class.java)
        assertThat(context.getBean(StopAdminPort::class.java)).isInstanceOf(IamStopAdminAdapter::class.java)
        assertThat(context.getBean(ReservationAdminPort::class.java)).isInstanceOf(IamReservationAdminAdapter::class.java)
    }

    @Test
    fun `실제 핸들러 매핑은 모두 라우트 허용 목록 안에 있고 internal 경로가 없다`() {
        val paths = handlerMapping.handlerMethods.keys.flatMap { it.patternValues }
        // /error 는 Spring Boot 기본 오류 컨트롤러다. 공개 포트에서는 인증 필터가 직접 요청을 404로 끝낸다(아래 HTTP 테스트).
        val outside = paths.filterNot { it == "/error" || ApiPathPolicy.isRoutable(it, exposeApiDocs = false) }

        assertThat(paths).isNotEmpty
        assertThat(paths).noneMatch { it.startsWith("/api/v1/internal") }
        assertThat(outside).isEmpty()
    }

    @Test
    fun `Actuator 는 관리 포트에서만 열리고 공개 포트에서는 404 다`() {
        assertThat(get(managementPort, "/actuator/health").statusCode()).isEqualTo(200)
        assertThat(get(publicPort, "/actuator/health").statusCode()).isEqualTo(404)
        assertThat(get(publicPort, "/error").statusCode()).isEqualTo(404)
        assertThat(get(publicPort, "/api/v1/internal/stop").statusCode()).isEqualTo(404)
    }

    @Test
    fun `토큰 없는 보호 API 는 401, 토큰이 있어도 없는 경로는 500 이 아니라 404 다`() {
        assertThat(get(publicPort, "/api/v1/member/me").statusCode()).isEqualTo(401)

        val principal = MemberPrincipal(UUID.randomUUID(), "login", "a@b.c", MemberType.MEMBER, true, emptySet())
        val token = jwtProvider.generateAccessToken(principal, emptySet(), TokenAudience.USER)
        val response = get(publicPort, "/api/v1/stop/no-such-path", token)

        assertThat(response.statusCode()).isEqualTo(404)
        assertThat(response.body()).contains("\"code\":\"00280\"")
    }
}
