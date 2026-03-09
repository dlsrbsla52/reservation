package com.hig.autoconfigure;

import com.hig.security.JwtProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration
public class CommonSecurityAutoConfiguration {

    @Bean
    public JwtProvider jwtProvider(@Value("${jwt.secret}") String secret, StringRedisTemplate redisTemplate) {
        return new JwtProvider(secret, redisTemplate);
    }
}
