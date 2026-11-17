package com.media.bus.iam.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

/**
 * ## 비밀번호 해시 인코더
 *
 * 예전에는 iam `SecurityConfig` 안에 있었다. 모듈러 모놀리스 전환으로 보안 필터 체인은 `app` 모듈 하나로
 * 합쳤고, 비밀번호 해시는 iam 도메인 관심사라 iam에 남긴다.
 */
@Configuration
class PasswordEncoderConfig {

    /**
     * BCrypt strength 기본값(10)은 약 100ms 소요. 보안과 성능의 균형점.
     * Virtual Thread 환경에서도 BCrypt는 CPU-bound 작업이므로 별도 스레드 풀 격리 불필요.
     */
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
