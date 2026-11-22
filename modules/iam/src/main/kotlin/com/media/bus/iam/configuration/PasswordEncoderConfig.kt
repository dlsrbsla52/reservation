package com.media.bus.iam.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

/** 비밀번호 해시 인코더 */
@Configuration
class PasswordEncoderConfig {

    /** BCrypt strength 기본값(10)은 약 100ms 소요. */
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
