package com.media.bus

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/** 모듈러 모놀리스 진입점 */
@SpringBootApplication(
    scanBasePackages = [
        "com.media.bus.app",
        "com.media.bus.iam",
        "com.media.bus.stop",
        "com.media.bus.reservation",
        "com.media.bus.content",
    ],
)
class BusApplication

fun main(args: Array<String>) {
    runApplication<BusApplication>(*args)
}
