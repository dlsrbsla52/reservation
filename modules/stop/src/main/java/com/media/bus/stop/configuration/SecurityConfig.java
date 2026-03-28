package com.media.bus.stop.configuration;

import com.media.bus.contract.filter.S2STokenFilter;
import com.media.bus.contract.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

/// stop 서비스 Spring Security 설정.
/// 설계 원칙:
/// - Gateway가 JWT를 검증하므로 stop 서비스는 사용자 토큰을 재검증하지 않습니다.
/// - /api/v1/internal/\*\* 경로는 S2STokenFilter로 내부 서비스 호출만 허용합니다.
/// - 그 외 경로는 인증 없이 통과 (MemberPrincipalExtractFilter + AuthorizeHandlerInterceptor 담당).
/// 사이드 이펙트:
/// - 이 빈이 등록되면 common의 CommonSecurityAutoConfiguration(@ConditionalOnMissingBean)이 비활성화됩니다.
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtProvider jwtProvider;

    @Bean
    public S2STokenFilter s2sTokenFilter() {
        // S2STokenFilter.shouldNotFilter()가 이 경로 외의 요청을 건너뜁니다
        return new S2STokenFilter(jwtProvider, List.of("/api/v1/internal/"));
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(s2sTokenFilter(), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}