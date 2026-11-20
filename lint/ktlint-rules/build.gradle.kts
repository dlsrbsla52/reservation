import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

// bus 전용 ktlint 규칙. 루트 빌드가 includeBuild로 가져와 ktlint CLI 클래스패스에 올린다.
// 메인 빌드의 subproject로 두지 않는 이유: 루트 subprojects 블록이 Spring BOM을 적용해 kotlin-* 버전을 바꾼다.
plugins {
    kotlin("jvm") version "2.3.20"
}

group = "com.media.bus.lint"
version = "1.0.0"

val ktlintVersion = "1.8.0"

kotlin {
    jvmToolchain(25)
    compilerOptions {
        // ktlint 1.8.0은 Kotlin 2.2.21 표준 라이브러리로 돈다. 그보다 새 API를 쓰지 않게 막는다.
        apiVersion.set(KotlinVersion.KOTLIN_2_2)
        languageVersion.set(KotlinVersion.KOTLIN_2_2)
    }
}

dependencies {
    compileOnly("com.pinterest.ktlint:ktlint-cli-ruleset-core:$ktlintVersion")
    compileOnly("com.pinterest.ktlint:ktlint-rule-engine-core:$ktlintVersion")

    testImplementation("com.pinterest.ktlint:ktlint-cli-ruleset-core:$ktlintVersion")
    testImplementation("com.pinterest.ktlint:ktlint-rule-engine-core:$ktlintVersion")
    testImplementation("com.pinterest.ktlint:ktlint-test:$ktlintVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.14.1")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
}

tasks.test {
    useJUnitPlatform()
}
