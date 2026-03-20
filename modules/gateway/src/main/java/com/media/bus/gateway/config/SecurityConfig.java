package com.media.bus.gateway.config;

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
            // 인증/인가 판단은 JwtAuthenticationFilter(GlobalFilter)에 완전 위임
            // Spring Security 기본 인증 메커니즘(httpBasic, formLogin)은 모두 비활성화
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                .anyExchange().permitAll()
            );

        return http.build();
    }
}