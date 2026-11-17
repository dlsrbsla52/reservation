package com.media.bus.app.architecture

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/**
 * ## 도메인 모듈 간 컴파일 의존 금지
 *
 * iam·stop·reservation 은 서로의 Gradle 프로젝트에 의존하지 않는다. 연결은 `app` 어댑터에서만 한다.
 * 의존이 생기면 다시 서비스로 쪼갤 때 비용이 커지고, 포트를 우회한 직접 호출이 가능해진다.
 */
class ModuleBoundaryTest {

    @Test
    fun `도메인 모듈 build 파일은 다른 도메인 모듈을 참조하지 않는다`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").exists() }
        val domains = listOf("iam", "stop", "reservation")
        val violations = domains.flatMap { module ->
            val build = File(root, "modules/$module/build.gradle.kts").readText()
            (domains - module).filter { build.contains("\":modules:$it\"") }.map { "$module -> $it" }
        }
        assertThat(violations).isEmpty()
    }
}
