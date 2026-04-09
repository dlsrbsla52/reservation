package com.media.bus.gateway.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain

@Configuration
@EnableWebFluxSecurity
class SecurityConfig {

    @Bean
    fun springSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        return http
            // MSA 게이트웨이 특성상 외부 클라이언트에서 직접 호출되므로 CSRF 비활성화 처리
            .csrf { it.disable() }
            // Spring Security가 Gateway 필터보다 먼저 실행되므로,
            // CorsConfig의 CorsConfigurationSource Bean을 참조하여 preflight(OPTIONS)를 직접 처리합니다.
            // 이 설정이 없으면 OPTIONS 요청이 403으로 차단됩니다.
            .cors(Customizer.withDefaults())
            // 인증/인가 판단은 JwtAuthenticationFilter(GlobalFilter)에 완전 위임
            // Spring Security 기본 인증 메커니즘(httpBasic, formLogin)은 모두 비활성화
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .authorizeExchange { exchanges ->
                exchanges.anyExchange().permitAll()
            }
            .build()
    }
}
