buildscript {
    val springBootVersion = "4.0.5"
    val kotlinVersion = "2.3.20"

    extra["springBootVersion"] = springBootVersion
    extra["kotlinVersion"] = kotlinVersion
    extra["exposedVersion"] = "1.0.0"

    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    dependencies {
        classpath("org.springframework.boot:spring-boot-gradle-plugin:$springBootVersion")
        classpath("io.spring.gradle:dependency-management-plugin:1.1.7")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("org.jetbrains.kotlin:kotlin-allopen:$kotlinVersion")
        // 제거: gradle.plugin.com.ewerk.gradle.plugins:querydsl-plugin
    }
}

plugins {
    id("io.sentry.jvm.gradle") version "4.11.0" apply false
    id("com.bmuschko.docker-spring-boot-application") version "10.0.0" apply false
}

allprojects {
    group = "com.media.bus"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "kotlin")
    apply(plugin = "kotlin-spring")  // open class 자동화: @Component, @Service, @Transactional 등
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")

    val springBootVersion: String by rootProject.extra

    // Spring Boot 4.0.5 BOM — 모든 하위 모듈에 버전 통일 강제
    // buildscript classpath로 로드된 플러그인은 Kotlin DSL 타입 안전 확장이 자동 생성되지 않으므로
    // configure<>()로 명시적 타입 캐스팅을 통해 dependencyManagement 확장에 접근한다.
    configure<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension> {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:$springBootVersion")
        }
    }

    // 라이브러리 모듈 기본값: bootJar 비활성화, jar 활성화
    tasks.named("bootJar") { enabled = false }
    tasks.named("jar") { enabled = true }

    // JDK 25 툴체인 — Java 컴파일 + 런타임
    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    // Kotlin 컴파일러 옵션
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            // Kotlin 2.3.20은 JVM 25 공식 지원
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
            // null 안전성 강화: JSR-305 어노테이션을 strict 모드로 처리
            freeCompilerArgs.addAll("-Xjsr305=strict")
        }
    }

    // Log4j2 전환: Logback 전이 의존성 전역 제거
    configurations.configureEach {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
        exclude(group = "ch.qos.logback", module = "logback-classic")
        exclude(group = "ch.qos.logback", module = "logback-core")
        // 제거: Lombok annotationProcessor는 더 이상 불필요
    }

    dependencies {
        // Kotlin 직렬화 지원 — data class JSON 변환
        "implementation"("com.fasterxml.jackson.module:jackson-module-kotlin")
        // Spring 리플렉션 지원 — @Component, @Autowired 등
        "implementation"("org.jetbrains.kotlin:kotlin-reflect")

        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        // MockK — Kotlin 친화적 Mock 프레임워크 (Mockito 대체)
        "testImplementation"("io.mockk:mockk:1.13.10")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.named<Test>("test") {
        useJUnitPlatform()
        // Java 25 환경에서 Mockito가 내부적으로 사용하는 Byte Buddy
        // 공식 지원 범위 초과 우회 — best-effort experimental 모드
        jvmArgs("-Dnet.bytebuddy.experimental=true")
    }
}
