package com.media.bus.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/// Gateway CORS 설정.
/// Spring Security 필터 체인이 Gateway 라우팅 필터보다 먼저 실행되므로,
/// Spring Security에도 동일한 CORS 설정을 명시적으로 등록해야 preflight(OPTIONS) 요청이 403으로 차단되지 않습니다.
/// SecurityConfig에서 `.cors(Customizer.withDefaults())`를 통해 이 Bean을 참조합니다.
@Configuration
public class CorsConfig {

    /// 운영 환경에서는 CORS_ALLOWED_ORIGIN 환경변수로 주입합니다.
    /// 로컬 개발 환경은 application-local.yml에서 http://localhost:3000 으로 설정됩니다.
    @Value("${CORS_ALLOWED_ORIGIN}")
    private String allowedOrigin;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // withCredentials: true 사용 시 allowedOrigins에 * 와일드카드 불가 — 명시적 origin 필수
        config.setAllowedOrigins(List.of(allowedOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
