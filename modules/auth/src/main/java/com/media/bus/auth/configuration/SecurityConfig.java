package com.media.bus.auth.configuration;

import com.media.bus.auth.configuration.filter.S2STokenFilter;
import com.media.bus.contract.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * auth 서비스 Spring Security 설정.
 * 설계 원칙:
 * - auth 서비스는 Gateway 내부 망에서만 호출됩니다. JWT 검증은 Gateway에서 수행합니다.
 * - /api/v1/member/** 경로는 S2STokenFilter로 내부 서비스 호출만 허용합니다.
 * - CSRF는 Stateless REST API 특성상 비활성화합니다.
 * 보안 Note:
 * 실제 운영 환경에서는 네트워크 레벨(Security Group, VPC)에서 Gateway를 통한 요청만
 * auth 서비스에 도달할 수 있도록 제한합니다.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtProvider jwtProvider;

    @Bean
    public S2STokenFilter s2sTokenFilter() {
        return new S2STokenFilter(jwtProvider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // S2STokenFilter는 /api/v1/member/** 경로에만 적용 (shouldNotFilter로 제어)
                .addFilterBefore(s2sTokenFilter(), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt strength 기본값(10)은 약 100ms 소요. 보안과 성능의 균형점.
        // Virtual Thread 환경에서도 BCrypt는 CPU-bound 작업이므로 별도 스레드 풀 격리 불필요.
        return new BCryptPasswordEncoder();
    }
}
