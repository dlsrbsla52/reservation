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

/** 단일 보안 필터 체인 */
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
            .addFilterBefore(
                JwtAuthenticationFilter(jwtProvider, exposeApiDocs, managementPort),
                UsernamePasswordAuthenticationFilter::class.java,
            )
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .build()
    }
}
