package com.media.bus.contract.autoconfigure;

import com.media.bus.contract.security.JwtProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration
public class AuthContractAutoConfiguration {

    @Bean
    public JwtProvider jwtProvider(
        @Value("${jwt.secret}") String secret,
        StringRedisTemplate redisTemplate
    ) {
        return new JwtProvider(secret, redisTemplate);
    }
}
