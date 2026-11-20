package com.media.bus.lint.ktlint

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import org.junit.jupiter.api.Test

class SourceRulesTest {

    private val factoryRule = assertThatRule { EntityFactoryNameRule() }
    private val exceptionRule = assertThatRule { NoHardcodedExceptionMessageRule() }

    @Test
    fun `Entity companion의 of 팩토리는 위반이다`() {
        val code = "class RoleEntity(id: EntityID<UUID>) : UUIDEntity(id) {\n" +
            "    companion object : UUIDEntityClass<RoleEntity>(RoleTable) {\n" +
            "        fun of(name: String): RoleEntity = new {}\n" +
            "    }\n" +
            "}\n"
        factoryRule(code).hasLintViolationWithoutAutoCorrect(3, 13, EntityFactoryNameRule.MESSAGE)
    }

    @Test
    fun `일반 클래스 companion의 of는 보지 않는다`() {
        factoryRule("data class Money(val v: Int) {\n    companion object {\n        fun of(v: Int) = Money(v)\n    }\n}\n")
            .hasNoLintViolations()
    }

    @Test
    fun `외부 예외에 문자열 리터럴을 넘기면 위반이다`() {
        exceptionRule("fun a(): Nothing = throw MalformedJwtException(\"sub 없음\")\n")
            .hasLintViolationWithoutAutoCorrect(1, 26, NoHardcodedExceptionMessageRule.MESSAGE)
    }

    @Test
    fun `require 람다가 문자열을 돌려주면 위반이다`() {
        exceptionRule("fun a(x: Int) {\n    require(x > 0) { \"양수\" }\n}\n")
            .hasLintViolationWithoutAutoCorrect(2, 5, NoHardcodedExceptionMessageRule.MESSAGE)
    }

    @Test
    fun `Result로 만든 예외와 로그 호출은 통과한다`() {
        exceptionRule("fun a() {\n    log.error(\"실패: {}\", 1)\n    throw BusinessException(StopResult.STOP_NOT_FOUND)\n}\n")
            .hasNoLintViolations()
    }
}
