plugins {
    `java-library`
}

// bootJar/jar 활성화 여부는 루트 build.gradle.kts subprojects 블록에서 일괄 설정
// (라이브러리 모듈: bootJar=false, jar=true)

val exposedVersion: String by rootProject.extra

dependencies {
    api("org.springframework.boot:spring-boot-starter-web")
    // 제거: spring-boot-starter-data-jpa → Exposed ORM으로 대체
    api("org.springframework.boot:spring-boot-starter-validation")
    api("org.springframework.boot:spring-boot-starter-security")
    // spring-aop는 spring-boot-starter-web이 전이적으로 포함. AspectJ 위빙 런타임만 명시 선언
    api("org.aspectj:aspectjweaver")

    // OpenAPI (Swagger) — springdoc 3.x: Spring Boot 4 / Spring Framework 7 지원
    api("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2")

    // Exposed ORM — JPA/QueryDSL 대체
    // exposed-spring-boot4-starter: Spring Boot 4.x + SpringTransactionManager 자동구성 포함
    api("org.jetbrains.exposed:exposed-spring-boot4-starter:$exposedVersion")
    api("org.jetbrains.exposed:exposed-core:$exposedVersion")
    api("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    api("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    // java.time.* 타입 지원 (OffsetDateTime, LocalDate 등)
    api("org.jetbrains.exposed:exposed-java-time:$exposedVersion")

    // 제거: querydsl-jpa, querydsl-core, querydsl-collections, querydsl-apt
    // 제거: annotationProcessor (Lombok, QueryDSL APT)

    // AWS SDK BOM 관리
    api(platform("software.amazon.awssdk:bom:2.39.2"))
    api("software.amazon.awssdk:s3")
    api("software.amazon.awssdk:lambda")
    api("software.amazon.awssdk:ses")

    // Resilience4j — Spring Boot 4 전용 artifact
    api("io.github.resilience4j:resilience4j-spring-boot4:2.4.0")

    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.postgresql:postgresql")

    // Liquibase — 각 모듈 DB 마이그레이션 (DDL 변경 없음)
    api("org.liquibase:liquibase-core")

    // Logging: Log4j2 + LMAX Disruptor 락프리 비동기 로깅
    // Virtual Thread 핀닝(pinning) 방지
    api("org.springframework.boot:spring-boot-starter-log4j2")
    api("com.lmax:disruptor:4.0.0")
    api("org.apache.logging.log4j:log4j-layout-template-json")

    // Micrometer Tracing + OpenTelemetry — traceId/spanId MDC 자동 주입
    api("org.springframework.boot:spring-boot-starter-actuator")
    api("io.micrometer:micrometer-tracing-bridge-otel")
}

// MdcLoggingFilter 동작 시각화 데모
// 실행: ./gradlew :modules:common:demoLogging
tasks.register<JavaExec>("demoLogging") {
    group = "demo"
    description = "MDC 로깅 필터 동작을 콘솔에 시각화합니다"
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    // Kotlin object로 변환 후에도 JVM 클래스명 동일 유지
    mainClass.set("com.media.bus.common.logging.MdcLoggingFilterDemo")
    jvmArgs("-Dnet.bytebuddy.experimental=true")
}
