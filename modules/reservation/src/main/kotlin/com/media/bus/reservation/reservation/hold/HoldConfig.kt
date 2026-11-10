package com.media.bus.reservation.reservation.hold

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/** 선점 정책 설정 바인딩과 만료 스케줄러 활성화. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(HoldProperties::class)
class HoldConfig
