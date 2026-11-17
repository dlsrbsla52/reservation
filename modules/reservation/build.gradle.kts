// 도메인 라이브러리 모듈. 실행은 modules/app 이 맡는다(bootJar=false, jar=true 는 루트 subprojects 기본값).
// 다른 도메인 모듈(iam/stop/reservation)에 의존하지 않는다 — 모듈 간 호출은 소비자가 정의한 포트를 app 이 구현한다.
dependencies {
    implementation(project(":modules:common"))
    implementation(project(":modules:auth-contract"))

}
