package com.hig.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
            // MSA 게이트웨이 특성상 외부 클라이언트에서 직접 호출되므로 CSRF 비활성화 처리
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                // 로드밸런서 및 컨테이너 오케스트레이션 헬스체크를 위한 엔드포인트 개방
                .pathMatchers("/health-check").permitAll()
                // 그 외 모든 요청은 인증 필요 (업무 요구사항에 따라 추후 라우팅 규칙에 맞게 수정 필요)
                .anyExchange().authenticated()
            );

        return http.build();
    }
}