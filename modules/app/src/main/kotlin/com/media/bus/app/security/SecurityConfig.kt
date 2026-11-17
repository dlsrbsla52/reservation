package com.media.bus.app.security

import com.media.bus.contract.security.JwtProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfigurationSource

/**
 * ## 단일 보안 필터 체인
 *
 * 예전에는 iam·stop·reservation이 각자 전체 요청 대상 체인을 가졌다. 한 컨텍스트에 함께 등록하면
 * `UnreachableFilterChainException`으로 기동하지 않으므로, 세 체인을 지우고 이 체인 하나로 합쳤다.
 *
 * - 인증·라우트 허용 목록·관리자 발급처 검사는 [JwtAuthenticationFilter]가 한다(예전 gateway 역할).
 * - 인가(카테고리·권한)는 지금처럼 컨트롤러의 `@Authorize`(auth-contract 인터셉터)가 한다.
 *   그래서 Spring Security 인가 규칙은 `permitAll`로 두고, 필터가 401·403·404를 직접 끝낸다.
 * - 공개 포트의 보안 체인은 이 빈 하나뿐이다. 예전에 `common`이 두던 "체인이 없으면 permitAll" 기본 체인은 지웠다.
 *   이 빈이 빠지면 인증 필터·라우트 허용 목록 없이 조용히 열리는 대신 Spring Boot 기본 체인(전부 인증 필요)으로 닫힌다.
 *   `BusApplicationContextTest`가 이 체인에 [JwtAuthenticationFilter]가 들어 있는지 확인한다.
 *
 * 관리 포트(8081, 루프백)로 들어온 요청은 인증 필터가 건너뛴다. 컨테이너 안 healthcheck가 토큰 없이 200을 받아야 한다.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtProvider: JwtProvider,
        corsConfigurationSource: CorsConfigurationSource,
        @Value("\${app.security.expose-api-docs:false}") exposeApiDocs: Boolean,
        environment: Environment,
    ): SecurityFilterChain {
        val configuredManagementPort = environment.getProperty("management.server.port", Int::class.java)
        // 0(임의 포트)이면 관리 서버가 뜬 뒤 Spring Boot 가 기록하는 실제 포트(local.management.port)를 쓴다. 운영(8081 고정)은 설정값 그대로다.
        val managementPort: () -> Int? = {
            if (configuredManagementPort == 0) environment.getProperty("local.management.port", Int::class.java)
            else configuredManagementPort
        }
        return http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            // 빈으로 등록하지 않고 체인에만 넣는다. 빈이면 Boot가 서블릿 필터로도 등록해 체인 밖에서 한 번 더 돈다.
            .addFilterBefore(
                JwtAuthenticationFilter(jwtProvider, exposeApiDocs, managementPort),
                UsernamePasswordAuthenticationFilter::class.java,
            )
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .build()
    }
}
