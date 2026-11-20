package com.media.bus.lint.ktlint

import com.pinterest.ktlint.rule.engine.core.api.ElementType
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtAnnotationEntry

/** 테스트 메서드에는 `@DisplayName`을 달지 않는다. 백틱 이름이 곧 표시 이름이다. 클래스의 `@DisplayName`은 허용한다. */
class NoMethodDisplayNameRule : BusRule("no-method-display-name") {

    override fun beforeVisitChildNodes(node: ASTNode, emit: Emit) {
        if (node.elementType != ElementType.ANNOTATION_ENTRY) return
        val entry = node.psi as? KtAnnotationEntry ?: return
        if (entry.shortName?.asString() != "DisplayName") return
        if (node.treeParent?.treeParent?.elementType != ElementType.FUN) return
        emit(node.startOffset, MESSAGE, true).ifAutocorrectAllowed { node.removeWithLine() }
    }

    companion object {
        const val MESSAGE = "[bus:no-method-display-name] 테스트 메서드에 @DisplayName을 달지 않는다. 백틱 한국어 이름이 표시 이름이다."
    }
}
