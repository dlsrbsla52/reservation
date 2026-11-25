plugins {
    id("org.springdoc.openapi-gradle-plugin")
}

// 실행 모듈 — 루트 subprojects 기본값(bootJar=false)을 되돌린다.
// 산출물 이름을 app.jar 로 고정해 Dockerfile 이 정확한 파일을 복사하게 한다.
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = true
    archiveFileName.set("app.jar")
    // 문서 생성 전용 프로필과 H2 는 실행 JAR 에 넣지 않는다
    exclude("application-openapi.yml")
    setClasspath(classpath!!.filter { !it.name.startsWith("h2-") })
}
tasks.named("jar") { enabled = false }

dependencies {
    implementation(project(":modules:iam"))
    implementation(project(":modules:stop"))
    implementation(project(":modules:reservation"))
    implementation(project(":modules:content"))
    // 도메인 모듈의 common·auth-contract 의존은 implementation 이라 전이되지 않으므로 직접 선언한다
    implementation(project(":modules:auth-contract"))
    implementation(project(":modules:common"))
    "developmentOnly"("com.h2database:h2")
    testRuntimeOnly("com.h2database:h2")
}

// 통합 OpenAPI 명세. 앱을 openapi 프로필(H2, 127.0.0.1:8090)로 띄운 뒤 내려받는다.
openApi {
    apiDocsUrl.set("http://127.0.0.1:8090/api-docs.yaml")
    outputDir.set(layout.buildDirectory.dir("docs/openapi"))
    outputFileName.set("openapi.yaml")
    waitTimeInSeconds.set(90)
    customBootRun {
        args.set(listOf("--spring.profiles.active=openapi"))
    }
}

tasks.named("forkedSpringBootRun") {
    dependsOn(
        ":modules:common:jar",
        ":modules:auth-contract:jar",
        ":modules:iam:jar",
        ":modules:stop:jar",
        ":modules:reservation:jar",
        ":modules:content:jar",
    )
}
