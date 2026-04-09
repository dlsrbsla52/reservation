package com.media.bus.common.configuration

import com.media.bus.common.client.S2SRestClientFactory
import com.media.bus.common.security.TokenProvider
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.util.StringUtils
import org.springframework.web.client.RestClient
import java.net.URI

/**
 * ## 내부 통신용 RestClient 설정
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class RestClientConfig(
    private val tokenProvider: TokenProvider,
    private val requestProvider: ObjectProvider<HttpServletRequest>,
) {

    /**
     * S2S 전용 RestClient 팩토리 Bean.
     * S2S 호출이 필요한 클라이언트는 이 팩토리를 주입받아 baseUrl을 지정해 RestClient를 생성한다.
     */
    @Bean
    fun s2sRestClientFactory(): S2SRestClientFactory = S2SRestClientFactory(tokenProvider)

    /**
     * 내부 통신용 RestClient Bean.
     * 시스템 간 내부 호출 시 이 Bean을 의존성 주입(DI) 받아 사용한다.
     * 호출 시 현재 스레드의 Security Context (Authorization Token)를 자동으로 전파한다.
     * Spring Boot 4에서 RestClient.Builder 자동 빈 등록이 제거되어 정적 팩토리 메서드로 직접 생성한다.
     */
    @Bean
    fun internalRestClient(): RestClient = buildWith(RestClient.builder())

    /**
     * 테스트 전용: MockRestServiceServer 바인딩이 적용된 Builder를 받아 RestClient를 조립한다.
     * MockRestServiceServer.bindTo(builder)로 먼저 목 서버를 바인딩한 뒤 이 메서드에 전달하세요.
     */
    internal fun buildWith(builder: RestClient.Builder): RestClient =
        builder
            // Security Context 전파 헤더 주입 Interceptor 등록
            .requestInterceptor(securityContextPropagationInterceptor())
            .build()

    /**
     * 현재 스레드의 HTTP Request에서 Authorization 토큰을 추출해
     * RestClient Request Header에 주입하는 Interceptor 로직.
     */
    private fun securityContextPropagationInterceptor(): ClientHttpRequestInterceptor =
        ClientHttpRequestInterceptor { request, body, execution ->
            // 보안 강화: 요청 대상이 신뢰할 수 있는 내부(Internal) 엔드포인트인 경우에만 토큰 주입
            if (isTrustedInternalService(request.uri)) {
                val token = extractBearerToken()

                if (StringUtils.hasText(token)) {
                    // 1. 유저 요청에 의한 컨텍스트가 존재할 경우: 유저 토큰 주입 (User-to-System)
                    request.headers.add(HttpHeaders.AUTHORIZATION, "Bearer $token")
                } else {
                    // 2. 비동기/스케줄러 등 컨텍스트가 없는 환경: S2S(System-to-System) 토큰 주입
                    val systemToken = tokenProvider.generateS2SToken()
                    request.headers.add(HttpHeaders.AUTHORIZATION, "Bearer $systemToken")
                }
            }

            // Interceptor Chain 실행 (다음 필터 또는 실제 HTTP 요청 수행)
            execution.execute(request, body)
        }

    /**
     * 요청 목적지 URI가 신뢰할 수 있는 내부 서비스인지 검증한다.
     * 외부(External) API 호출 시 인증 정보가 유출되는 것을 원천 차단한다.
     */
    private fun isTrustedInternalService(uri: URI): Boolean {
        val host = uri.host ?: return false

        // MSA 환경의 내부 도메인 패턴 (예: 서비스 명, K8s 내부 DNS, 특정 VPC 서브넷 대역 등)
        // 실제 운영 환경에서는 application.yml의 설정값(List)으로 관리하는 것을 권장한다.
        return host.endsWith(".local") ||
            host.endsWith(".internal") ||
            host.contains("service") || // 예: member-service, auth-service
            host == "localhost" ||
            host.startsWith("10.") // 내부망 IP 대역 예시
    }

    /**
     * ObjectProvider를 통해 현재 요청의 Authorization 헤더에서 Bearer 토큰을 추출한다.
     * RequestContextHolder(ThreadLocal 기반) 대신 ObjectProvider를 사용하여
     * Virtual Thread 환경에서 ThreadLocal 규약 위반 없이 안전하게 동작한다.
     * 비동기/스케줄러 컨텍스트(요청 없음)에서는 null을 반환한다.
     */
    private fun extractBearerToken(): String? {
        val request = requestProvider.getIfAvailable() ?: return null
        val bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION)
        return if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            bearerToken.substring(7)
        } else {
            null
        }
    }
}
