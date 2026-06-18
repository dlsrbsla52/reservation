plugins {
    id("org.springdoc.openapi-gradle-plugin")
}

// 루트 subprojects 기본값(bootJar=false)을 실행 모듈에서 명시적으로 재활성화
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = true
    exclude("application-openapi.yml")
    setClasspath(classpath!!.filter { !it.name.startsWith("h2-") })
}
tasks.named("jar") { enabled = false }

dependencies {
    implementation(project(":modules:common"))
    implementation(project(":modules:auth-contract"))
    "developmentOnly"("com.h2database:h2")
    testRuntimeOnly("com.h2database:h2")
}

openApi {
    apiDocsUrl.set("http://localhost:8181/api-docs.yaml")
    outputDir.set(layout.buildDirectory.dir("docs/openapi"))
    outputFileName.set("openapi-iam.yaml")
    waitTimeInSeconds.set(60)
    customBootRun {
        args.set(listOf("--spring.profiles.active=openapi"))
    }
}

tasks.named("forkedSpringBootRun") {
    dependsOn(":modules:common:jar", ":modules:auth-contract:jar")
}
