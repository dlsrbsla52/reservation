// 도메인 라이브러리 모듈(홈페이지 콘텐츠: 포트폴리오·공지·FAQ·온라인 문의). 실행은 modules/app 이 맡는다.
// 다른 도메인 모듈(iam/stop/reservation)에 의존하지 않는다 — 관리자 기능은 iam 포트를 app 어댑터가 구현해 호출한다.
dependencies {
    implementation(project(":modules:common"))
    implementation(project(":modules:auth-contract"))
    testRuntimeOnly("com.h2database:h2")
}
