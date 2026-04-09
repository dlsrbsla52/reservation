tasks.named("bootJar") { enabled = true }
tasks.named("jar") { enabled = false }

// Gateway는 WebFlux(Netty) 기반이어야 하므로,
// common/auth-contract에서 전이되는 Tomcat을 모든 configuration에서 전역 제거
configurations.all {
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    // Spring Cloud Gateway 5.x WebFlux는 spring-webmvc가 classpath에 있으면 라우팅 비활성화
    // common 모듈이 spring-boot-starter-web을 전이 의존성으로 끌어오므로 명시적으로 제외
    exclude(group = "org.springframework", module = "spring-webmvc")
}

dependencies {
    implementation(project(":modules:common"))
    implementation(project(":modules:auth-contract"))
    // Spring Cloud Gateway 5.0.x: WebFlux(Netty) 기반 게이트웨이
    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")
}

dependencyManagement {
    imports {
        // Spring Cloud 2025.1.1 (Oakwood) — Spring Boot 4.0.x 공식 지원 Release Train
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.1")
    }
}
