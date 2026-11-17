package com.media.bus

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * ## 모듈러 모놀리스 진입점
 *
 * iam·stop·reservation 도메인 모듈을 한 JVM에서 실행한다.
 *
 * 스캔 범위를 도메인 모듈과 `app`으로 명시한다. `com.media.bus` 전체를 스캔하면 `common`·`auth-contract`의
 * 클래스까지 컴포넌트 스캔에 걸린다. 두 라이브러리는 자동 구성(`AutoConfiguration.imports`)으로만 빈을 등록한다.
 */
@SpringBootApplication(
    scanBasePackages = [
        "com.media.bus.app",
        "com.media.bus.iam",
        "com.media.bus.stop",
        "com.media.bus.reservation",
    ],
)
class BusApplication

fun main(args: Array<String>) {
    runApplication<BusApplication>(*args)
}
