package com.media.bus.reservation.config

import com.media.bus.contract.filter.S2STokenFilter
import com.media.bus.contract.security.JwtProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/**
 * ## reservation 서비스 Spring Security 설정
 *
 * 설계 원칙:
 * - reservation 서비스는 Gateway 내부 망에서 호출된다. 일반 사용자 JWT 검증은 Gateway에서 수행한다.
 * - `/api/v1/internal/` 하위 전체 경로는 `S2STokenFilter`로 내부 서비스 호출만 허용한다.
 *   (iam 어드민의 `AdminBusinessContractFacade`가 호출하는 매니저별 계약 조회 엔드포인트 등)
 * - CSRF는 Stateless REST API 특성상 비활성화한다.
 *
 * 이 빈이 등록되면 `CommonSecurityAutoConfiguration.defaultSecurityFilterChain`은
 * `@ConditionalOnMissingBean` 조건으로 인해 자동으로 비활성화된다.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtProvider: JwtProvider,
) {

    /**
     * `/api/v1/internal/` 하위 경로에만 S2S 토큰 검증을 적용하는 필터.
     * 그 외 경로(`/api/v1/contract/...` 등)는 Gateway가 주입한 X-User-* 헤더를
     * `MemberPrincipalExtractFilter`(auth-contract)가 추출하여 사용한다.
     */
    @Bean
    fun s2sTokenFilter(): S2STokenFilter =
        S2STokenFilter(jwtProvider, listOf("/api/v1/internal/"))

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            // S2STokenFilter는 /api/v1/internal/** 경로에만 적용 (shouldNotFilter로 제어)
            .addFilterBefore(s2sTokenFilter(), UsernamePasswordAuthenticationFilter::class.java)
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .build()
}
