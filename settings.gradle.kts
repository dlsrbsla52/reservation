rootProject.name = "bus"

include("modules:common")
include("modules:auth-contract")
include("modules:iam")
include("modules:reservation")
include("modules:stop")

// 실행 모듈 — 도메인 모듈을 한 JVM으로 묶고 모듈 간 포트 어댑터를 둔다
include("modules:app")

// >>> bus-lint
// bus 전용 ktlint 규칙(lint/ktlint-rules). subproject로 두면 루트 subprojects 블록의 Spring BOM이 kotlin-* 버전을 바꾼다.
includeBuild("lint/ktlint-rules")
// <<< bus-lint
