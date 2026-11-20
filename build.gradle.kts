buildscript {
    val springBootVersion = "4.1.2"
    val kotlinVersion = "2.3.20"

    extra["springBootVersion"] = springBootVersion
    extra["kotlinVersion"] = kotlinVersion
    extra["exposedVersion"] = "1.6.1"

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
    id("org.springdoc.openapi-gradle-plugin") version "1.9.0" apply false
    // >>> bus-lint
    id("de.thetaphi.forbiddenapis") version "3.11" apply false
    // <<< bus-lint
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
    apply(plugin = "kotlin-spring") // open class 자동화: @Component, @Service, @Transactional 등
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")

    val springBootVersion = rootProject.extra["springBootVersion"] as String

    // Spring Boot 4.1.2 BOM — 모든 하위 모듈에 버전 통일 강제
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
        // 테스트 에이전트가 부트스트랩 경로를 확장하므로 테스트 JVM에서만 CDS를 끈다.
        // Byte Buddy의 Unsafe 접근을 비활성화해 지원되는 클래스 주입 경로를 사용한다.
        jvmArgs("-Xshare:off", "-Dnet.bytebuddy.safe=true")
    }

    // >>> bus-lint
    // 바이트코드 금지 API 검사. 주석·문자열은 걸리지 않고 실제 참조만 잡는다. check에 자동으로 묶인다.
    // suppressAnnotations를 비워 @SuppressForbidden 같은 어노테이션으로 우회하지 못하게 한다.
    apply(plugin = "de.thetaphi.forbiddenapis")
    configure<de.thetaphi.forbiddenapis.gradle.CheckForbiddenApisExtension> {
        signaturesFiles = rootProject.files("lint/forbidden-apis.txt")
        suppressAnnotations = emptySet()
        ignoreFailures = false
    }
    // 예외 메시지 하드코딩 금지는 main 코드에만 건다. 테스트는 실패 상황을 흉내 내려고 예외를 직접 만든다.
    tasks.named<de.thetaphi.forbiddenapis.gradle.CheckForbiddenApis>("forbiddenApisMain") {
        signaturesFiles = signaturesFiles + rootProject.files("lint/forbidden-apis-main.txt")
    }
    tasks.named<de.thetaphi.forbiddenapis.gradle.CheckForbiddenApis>("forbiddenApisTest") {
        signaturesFiles = signaturesFiles + rootProject.files("lint/forbidden-apis-test.txt")
    }
    // Claude Stop hook(.claude/hooks/compile-gate.sh)이 바뀐 모듈마다 부르는 lint 묶음. 도구를 늘리면 여기에만 추가한다.
    tasks.register("busLint") {
        group = "verification"
        description = "모듈 lint 묶음(금지 API 등)"
        dependsOn("forbiddenApis")
    }
    // <<< bus-lint

    // openapi-gradle-plugin이 적용된 모듈(app)에만 적용.
    // generateOpenApiDocs는 outputDir 산출물 존재 여부만 보고 UP-TO-DATE를 판단하므로
    // 소스가 바뀌어도 재실행을 건너뛴다. 매번 output을 지우고 재생성해 최신 스펙을 보장한다.
    pluginManager.withPlugin("org.springdoc.openapi-gradle-plugin") {
        // 각 모듈의 openApi { outputDir } 설정과 동일한 규칙(build/docs/openapi)을 따른다.
        val cleanOpenApiDocs = tasks.register<Delete>("cleanOpenApiDocs") {
            description = "생성된 OpenAPI YAML 산출물을 삭제해 generateOpenApiDocs가 항상 재실행되도록 한다."
            delete(layout.buildDirectory.dir("docs/openapi"))
        }
        tasks.named("generateOpenApiDocs") {
            dependsOn(cleanOpenApiDocs)
        }
    }
}

tasks.register("generateAllOpenApiDocs") {
    group = "documentation"
    description = "모듈러 모놀리스 app 의 통합 OpenAPI YAML 문서를 생성합니다."
    dependsOn(":modules:app:generateOpenApiDocs")
}

// >>> bus-lint
// ktlint: .editorconfig 하나를 IntelliJ 코드 정리와 같이 쓴다. bus 규칙(lint/ktlint-rules)은 자동 수정을 지원한다.
// 루트 프로젝트에 두는 이유: subproject에는 Spring BOM이 걸려 ktlint가 쓰는 kotlin-compiler 버전이 바뀐다.
val ktlint: Configuration by configurations.creating {
    isCanBeConsumed = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
    }
}

dependencies {
    ktlint("com.pinterest.ktlint:ktlint-cli:1.8.0")
    ktlint("com.media.bus.lint:ktlint-rules:1.0.0")
}

// ktlint도 프로젝트 기준인 JDK 25로 실행한다(데몬 JVM과 무관하게 고정).
apply(plugin = "jvm-toolchains")
val ktlintLauncher = extensions.getByType<JavaToolchainService>().launcherFor {
    languageVersion.set(JavaLanguageVersion.of(25))
}

fun registerKtlint(name: String, patterns: List<String>, format: Boolean) = tasks.register<JavaExec>(name) {
    group = if (format) "formatting" else "verification"
    description = if (format) "ktlint 자동 수정: ${patterns.joinToString()}" else "ktlint 검사: ${patterns.joinToString()}"
    classpath = ktlint
    javaLauncher.set(ktlintLauncher)
    // ktlint에 내장된 kotlin-compiler가 sun.misc.Unsafe를 써 JDK 25에서 경고가 쏟아진다. hook 출력이 묻히지 않게 끈다.
    jvmArgs("--sun-misc-unsafe-memory-access=allow")
    mainClass.set("com.pinterest.ktlint.Main")
    workingDir = rootDir
    args(listOfNotNull("--relative", if (format) "--format" else null) + patterns)
    // 자동 수정은 고칠 수 없는 위반이 남아도 나머지 수정을 반영하고 끝낸다. 검사는 위반이 있으면 실패한다.
    isIgnoreExitValue = format
}

val ktlintModules = subprojects.filter { it.file("src").isDirectory }
ktlintModules.forEach { module ->
    val patterns = listOf("${module.projectDir.relativeTo(rootDir).path}/src/**/*.kt")
    registerKtlint("ktlintCheck-${module.name}", patterns, format = false)
    registerKtlint("ktlintFormat-${module.name}", patterns, format = true)
    module.tasks.named("busLint") { dependsOn(":ktlintCheck-${module.name}") }
    module.tasks.named("check") { dependsOn(":ktlintCheck-${module.name}") }
}
tasks.register("ktlintCheck") {
    group = "verification"
    description = "모든 모듈 ktlint 검사"
    dependsOn(ktlintModules.map { ":ktlintCheck-${it.name}" })
}
tasks.register("ktlintFormat") {
    group = "formatting"
    description = "모든 모듈 ktlint 자동 수정"
    dependsOn(ktlintModules.map { ":ktlintFormat-${it.name}" })
}
// <<< bus-lint
