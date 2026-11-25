package com.media.bus.app.web

import org.springframework.boot.test.context.SpringBootTest

/** 앱 전체 컨텍스트를 H2·더미 비밀값으로 띄우는 테스트용 메타 어노테이션 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "management.server.port=0",
        "spring.datasource.url=jdbc:h2:mem:bus-web-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.liquibase.enabled=false",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=1",
        "management.health.redis.enabled=false",
        "reservation.hold.expiry-enabled=false",
        "content.inquiry.purge-enabled=false",
        "cookie.secure=false",
        "jwt.secret=context-test-only-secret-key-at-least-32-bytes-long",
        "jwt.claim-encryption-key=dGVzdC1vbmx5LWR1bW15LWNsYWltLWtleS0zMmJ5dGU=",
        "cors.allowed-origin=http://localhost:3000",
        "cors.admin-allowed-origin=http://admin.localhost:3001",
        "seoul.api.key=dummy",
        "app.security.expose-api-docs=false",
    ],
)
annotation class BusAppContextTest
