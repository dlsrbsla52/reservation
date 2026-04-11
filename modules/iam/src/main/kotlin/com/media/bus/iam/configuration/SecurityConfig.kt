package com.media.bus.iam.configuration

import com.media.bus.contract.filter.S2STokenFilter
import com.media.bus.contract.security.JwtProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/**
 * ## auth 서비스 Spring Security 설정
 *
 * 설계 원칙:
 * - auth 서비스는 Gateway 내부 망에서만 호출된다. JWT 검증은 Gateway에서 수행한다.
 * - /api/v1/member 하위 전체 경로는 `S2STokenFilter`로 내부 서비스 호출만 허용한다.
 * - CSRF는 Stateless REST API 특성상 비활성화한다.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtProvider: JwtProvider,
) {
    @Bean
    fun s2sTokenFilter(): S2STokenFilter =
        S2STokenFilter(jwtProvider, listOf("/api/v1/member/internal/"))

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            // S2STokenFilter는 /api/v1/member/** 경로에만 적용 (shouldNotFilter로 제어)
            .addFilterBefore(s2sTokenFilter(), UsernamePasswordAuthenticationFilter::class.java)
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .build()

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        // BCrypt strength 기본값(10)은 약 100ms 소요. 보안과 성능의 균형점.
        // Virtual Thread 환경에서도 BCrypt는 CPU-bound 작업이므로 별도 스레드 풀 격리 불필요.
        return BCryptPasswordEncoder()
    }
}
