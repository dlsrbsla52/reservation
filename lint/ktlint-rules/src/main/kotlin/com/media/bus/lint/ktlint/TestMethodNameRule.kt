package com.media.bus.lint.ktlint

import com.pinterest.ktlint.rule.engine.core.api.ElementType
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtNamedFunction

/** 테스트 메서드 이름은 백틱으로 감싼 한국어 문장이다. */
class TestMethodNameRule : BusRule("test-method-name") {

    override fun beforeVisitChildNodes(node: ASTNode, emit: Emit) {
        if (node.elementType != ElementType.FUN) return
        val function = node.psi as? KtNamedFunction ?: return
        if (function.annotationEntries.none { it.shortName?.asString() in TEST_ANNOTATIONS }) return
        val identifier = function.nameIdentifier ?: return
        if (identifier.text.startsWith("`") && HANGUL.containsMatchIn(identifier.text)) return
        emit(identifier.node.startOffset, MESSAGE, false)
    }

    companion object {
        private val TEST_ANNOTATIONS = setOf("Test", "ParameterizedTest", "RepeatedTest", "TestFactory", "TestTemplate")
        private val HANGUL = Regex("[\\uAC00-\\uD7A3]")
        const val MESSAGE =
            "[bus:test-method-name] 테스트 이름은 백틱으로 감싼 한국어 문장으로 쓴다. 예: fun `정류소가 없으면 404를 반환한다`()"
    }
}
