package com.hig.configuration;

import com.hig.security.JwtProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(RestClient.class)
public class RestClientConfig {

    private final JwtProvider jwtProvider;

    public RestClientConfig(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    /**
     * 내부 통신용 RestClient Bean
     * 시스템 간 내부 호출 시 이 Bean을 의존성 주입(DI) 받아 사용합니다.
     * 호출 시 현재 스레드의 Security Context (Authorization Token)를 자동으로 전파합니다.
     */
    @Bean
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public RestClient internalRestClient(RestClient.Builder builder) {
        return builder
                // Security Context 전파 헤더 주입 Interceptor 등록
                .requestInterceptor(securityContextPropagationInterceptor())
                .build();
    }

    /**
     * 현재 스레드의 HTTP Request에서 Authorization 토큰을 추출해
     * RestClient Request Header에 주입하는 Interceptor 로직입니다.
     */
    private ClientHttpRequestInterceptor securityContextPropagationInterceptor() {
        return (request, body, execution) -> {
            // 보안 강화: 요청 대상이 신뢰할 수 있는 내부(Internal) 엔드포인트인 경우에만 토큰 주입
            if (isTrustedInternalService(request.getURI())) {
                String token = extractBearerToken();

                if (StringUtils.hasText(token)) {
                    // 1. 유저 요청에 의한 컨텍스트가 존재할 경우: 유저 토큰 주입 (User-to-System)
                    request.getHeaders().add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                } else {
                    // 2. 비동기/스케줄러 등 컨텍스트가 없는 환경: S2S(System-to-System) 토큰 주입
                    // JwtProvider를 통해 실제 서명된 JWT 발급 (하드코딩된 문자열 제거)
                    String systemToken = jwtProvider.generateS2SToken();
                    request.getHeaders().add(HttpHeaders.AUTHORIZATION, "Bearer " + systemToken);
                }
            }

            // Interceptor Chain 실행 (다음 필터 또는 실제 HTTP 요청 수행)
            return execution.execute(request, body);
        };
    }

    /**
     * 요청 목적지 URI가 신뢰할 수 있는 내부 서비스인지 검증합니다.
     * 외부(External) API 호출 시 인증 정보가 유출되는 것을 원천 차단합니다.
     */
    private boolean isTrustedInternalService(java.net.URI uri) {
        String host = uri.getHost();
        if (host == null)
            return false;

        // MSA 환경의 내부 도메인 패턴 (예: 서비스 명, K8s 내부 DNS, 특정 VPC 서브넷 대역 등)
        // 실제 운영 환경에서는 application.yml의 설정값(List)으로 관리하는 것을 권장합니다.
        return host.endsWith(".local") ||
                host.endsWith(".internal") ||
                host.contains("service") || // 예: member-service, auth-service
                host.equals("localhost") ||
                host.startsWith("10."); // 내부망 IP 대역 예시
    }

    /**
     * RequestContextHolder를 참조하여 현재 유저의 토큰을 안전하게 추출합니다.
     * 주의: 비동기(@Async) 스레드 또는 스케줄러 환경에서는 값을 가져오지 못하므로 대체 전략이 필요합니다.
     */
    private String extractBearerToken() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
                return bearerToken.substring(7);
            }
        }
        return null;
    }
}
