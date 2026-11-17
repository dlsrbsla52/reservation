rootProject.name = "bus"

include("modules:common")
include("modules:auth-contract")
include("modules:iam")
include("modules:reservation")
include("modules:stop")

// 실행 모듈 — 도메인 모듈을 한 JVM으로 묶고 모듈 간 포트 어댑터를 둔다
include("modules:app")
