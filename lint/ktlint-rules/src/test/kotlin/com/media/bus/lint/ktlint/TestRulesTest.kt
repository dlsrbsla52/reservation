package com.media.bus.lint.ktlint

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import org.junit.jupiter.api.Test

class TestRulesTest {

    private val nameRule = assertThatRule { TestMethodNameRule() }
    private val displayNameRule = assertThatRule { NoMethodDisplayNameRule() }

    @Test
    fun `영어 테스트 이름은 위반이다`() {
        nameRule("class T {\n    @Test\n    fun shouldWork() {}\n}\n")
            .hasLintViolationWithoutAutoCorrect(3, 9, TestMethodNameRule.MESSAGE)
    }

    @Test
    fun `백틱 영어 테스트 이름도 위반이다`() {
        nameRule("class T {\n    @Test\n    fun `should work`() {}\n}\n")
            .hasLintViolationWithoutAutoCorrect(3, 9, TestMethodNameRule.MESSAGE)
    }

    @Test
    fun `백틱 한국어 테스트 이름은 통과한다`() {
        nameRule("class T {\n    @Test\n    fun `정류소가 없으면 404를 반환한다`() {}\n}\n").hasNoLintViolations()
    }

    @Test
    fun `테스트가 아닌 함수 이름은 보지 않는다`() {
        nameRule("class T {\n    private fun helper() {}\n}\n").hasNoLintViolations()
    }

    @Test
    fun `메서드의 DisplayName은 지운다`() {
        val code = "class T {\n    @Test\n    @DisplayName(\"설명\")\n    fun `동작한다`() {}\n}\n"
        displayNameRule(code)
            .hasLintViolation(3, 5, NoMethodDisplayNameRule.MESSAGE)
            .isFormattedAs("class T {\n    @Test\n    fun `동작한다`() {}\n}\n")
    }

    @Test
    fun `클래스의 DisplayName은 허용한다`() {
        displayNameRule("@DisplayName(\"정류소\")\nclass T\n").hasNoLintViolations()
    }
}
