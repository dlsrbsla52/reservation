package com.media.bus.lint.ktlint

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import org.junit.jupiter.api.Test

class CommentPolicyRuleTest {

    private val assertThatCode = assertThatRule { CommentPolicyRule() }

    @Test
    fun `혼자 있는 줄 주석은 줄째 지우고 앞의 빈 줄은 남긴다`() {
        val code = "class A {\n    val a = 1\n\n    // 설명\n    val b = 2\n}\n"
        assertThatCode(code)
            .hasLintViolation(4, 5, CommentPolicyRule.COMMENT_MESSAGE)
            .isFormattedAs("class A {\n    val a = 1\n\n    val b = 2\n}\n")
    }

    @Test
    fun `앞뒤에 빈 줄이 있는 구분선 주석을 지우면 빈 줄은 하나만 남는다`() {
        assertThatCode("class A {\n    val a = 1\n\n    // ─── 구분 ───\n\n    val b = 2\n}\n")
            .hasLintViolation(4, 5, CommentPolicyRule.COMMENT_MESSAGE)
            .isFormattedAs("class A {\n    val a = 1\n\n    val b = 2\n}\n")
    }

    @Test
    fun `줄 끝 주석은 주석만 지운다`() {
        assertThatCode("val a = 1 // 설명\n")
            .hasLintViolation(1, 11, CommentPolicyRule.COMMENT_MESSAGE)
            .isFormattedAs("val a = 1\n")
    }

    @Test
    fun `블록 끝 직전 주석을 지워도 닫는 괄호 들여쓰기는 그대로다`() {
        assertThatCode("fun a() {\n    val x = 1\n    // 끝\n}\n")
            .hasLintViolation(3, 5, CommentPolicyRule.COMMENT_MESSAGE)
            .isFormattedAs("fun a() {\n    val x = 1\n}\n")
    }

    @Test
    fun `예외 표시 주석은 남긴다`() {
        assertThatCode("fun a() {\n    // 예외: 외부 API가 빈 본문을 200으로 준다\n    val x = 1\n}\n").hasNoLintViolations()
    }

    @Test
    fun `함수의 여러 줄 KDoc은 첫 문장 한 줄로 줄인다`() {
        val code = "/**\n * 정류소를 찾는다. 없으면 null이다.\n *\n * 자세한 설명\n */\nfun find() {}\n"
        assertThatCode(code)
            .hasLintViolation(1, 1, CommentPolicyRule.KDOC_ONE_LINE_MESSAGE)
            .isFormattedAs("/** 정류소를 찾는다. */\nfun find() {}\n")
    }

    @Test
    fun `클래스 KDoc이 제목으로 시작하면 제목을 한 줄 요약으로 쓴다`() {
        val code = "/**\n * ## 정류소 서비스\n *\n * 설명\n */\nclass StopService\n"
        assertThatCode(code)
            .hasLintViolation(1, 1, CommentPolicyRule.KDOC_ONE_LINE_MESSAGE)
            .isFormattedAs("/** 정류소 서비스 */\nclass StopService\n")
    }

    @Test
    fun `파라미터 KDoc은 지운다`() {
        val code = "class A(\n    /** 이름 */\n    val name: String,\n)\n"
        assertThatCode(code)
            .hasLintViolation(2, 5, CommentPolicyRule.KDOC_OWNER_MESSAGE)
            .isFormattedAs("class A(\n    val name: String,\n)\n")
    }

    @Test
    fun `함수와 클래스의 한 줄 KDoc은 허용한다`() {
        assertThatCode("/** 정류소 서비스 */\nclass S {\n    /** 찾는다. */\n    fun find() {}\n}\n").hasNoLintViolations()
    }
}
