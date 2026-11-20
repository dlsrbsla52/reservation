package com.media.bus.lint.ktlint

import com.pinterest.ktlint.rule.engine.core.api.ElementType
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/** Exposed Entity의 정적 팩토리(companion `*EntityClass`)는 `create`로 짓는다. */
class EntityFactoryNameRule : BusRule("entity-factory-name") {

    override fun beforeVisitChildNodes(node: ASTNode, emit: Emit) {
        if (node.elementType != ElementType.FUN) return
        val function = node.psi as? KtNamedFunction ?: return
        if (function.name !in FORBIDDEN_NAMES) return
        val companion = function.getStrictParentOfType<KtObjectDeclaration>() ?: return
        if (!companion.isCompanion()) return
        if (companion.superTypeListEntries.none { it.text.contains("EntityClass") }) return
        emit(function.nameIdentifier?.node?.startOffset ?: node.startOffset, MESSAGE, false)
    }

    companion object {
        private val FORBIDDEN_NAMES = setOf("of", "from", "newInstance")
        const val MESSAGE = "[bus:entity-factory-name] Entity 정적 팩토리는 create(또는 createXxx)로 짓는다."
    }
}
