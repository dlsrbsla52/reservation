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
        // Spring Cloud 2025.1.4 (Oakwood) — Spring Boot 4.1.x 대응 Release Train.
        // 2025.1.2의 spring-cloud-commons가 끌어오던 bcprov-jdk18on 1.81.1(CVE-2026-8763 등)을 1.85.2로 올린다.
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.4")
        // Spring Cloud BOM이 resilience4j를 2.3.0으로 고정해 common의 resilience4j-spring-boot4:2.4.0과
        // 버전이 섞인다(런타임 NoSuchMethodError 위험). 뒤에 import한 BOM이 우선하므로 2.4.0으로 다시 맞춘다.
        mavenBom("io.github.resilience4j:resilience4j-bom:2.4.0")
    }
}
