package com.media.bus.gateway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/// Spring Boot 4에서 auto-configuration이 모듈별 패키지로 분리됨.
/// 컴파일 타임 의존성 없이 제외하기 위해 excludeName(문자열 기반) 방식을 사용한다.
///
/// 패키지 변경 내역:
/// - org.springframework.boot.autoconfigure.jdbc       → org.springframework.boot.jdbc.autoconfigure
/// - org.springframework.boot.autoconfigure.orm.jpa    → org.springframework.boot.hibernate.autoconfigure
/// - org.springframework.boot.autoconfigure.liquibase  → org.springframework.boot.liquibase.autoconfigure
/// - org.springframework.boot.autoconfigure.security.reactive → org.springframework.boot.security.autoconfigure
/// - org.springframework.boot.autoconfigure.web.servlet → org.springframework.boot.webmvc.autoconfigure
@SpringBootApplication(excludeName = [
    "org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration",
    "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
    "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
    "org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration",
    "org.springframework.boot.security.autoconfigure.ReactiveUserDetailsServiceAutoConfiguration",
    // Gateway는 DB 없음 — Exposed 자동구성이 DataSource를 요구하므로 제외
    "org.jetbrains.exposed.v1.spring.boot4.autoconfigure.ExposedAutoConfiguration",
])
class GatewayApplication

fun main(args: Array<String>) {
    runApplication<GatewayApplication>(*args)
}
