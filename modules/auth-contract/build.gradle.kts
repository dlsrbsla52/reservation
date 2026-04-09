plugins {
    `java-library`
}

// bootJar/jar 활성화 여부는 루트 build.gradle.kts subprojects 블록에서 일괄 설정
// (라이브러리 모듈: bootJar=false, jar=true)

dependencies {
    api(project(":modules:common"))

    // JWT (JSON Web Token)
    api("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")

    // Redis — Refresh Token 저장소
    api("org.springframework.boot:spring-boot-starter-data-redis")
}
