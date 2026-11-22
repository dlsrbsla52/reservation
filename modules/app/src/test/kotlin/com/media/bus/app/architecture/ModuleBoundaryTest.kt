package com.media.bus.app.architecture

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/** 도메인 모듈 간 컴파일 의존 금지 */
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
