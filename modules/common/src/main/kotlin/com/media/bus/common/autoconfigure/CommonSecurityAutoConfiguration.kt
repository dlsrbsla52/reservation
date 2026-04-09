package com.media.bus.common.autoconfigure

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

/**
 * ## JWT 인증을 Gateway에 위임하는 서비스들을 위한 기본 Security 자동 구성
 *
 * - Gateway가 JWT를 검증하고 X-User-* 헤더를 주입하므로, 하위 서비스는 CSRF/세션 불필요.
 * - 자체 SecurityFilterChain을 등록한 서비스(auth 등)는 이 구성이 적용되지 않음.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class CommonSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain::class)
    fun defaultSecurityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { csrf: CsrfConfigurer<HttpSecurity> -> csrf.disable() }
            .sessionManagement { session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth -> auth.anyRequest().permitAll() }
            .build()
}
