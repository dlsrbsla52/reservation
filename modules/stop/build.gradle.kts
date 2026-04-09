// 루트 subprojects 기본값(bootJar=false)을 실행 모듈에서 명시적으로 재활성화
tasks.named("bootJar") { enabled = true }
tasks.named("jar") { enabled = false }

dependencies {
    implementation(project(":modules:common"))
    implementation(project(":modules:auth-contract"))
    testRuntimeOnly("com.h2database:h2")
}
