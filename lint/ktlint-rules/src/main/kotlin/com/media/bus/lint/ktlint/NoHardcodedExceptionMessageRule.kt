package com.media.bus.lint.ktlint

import com.pinterest.ktlint.rule.engine.core.api.ElementType
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/** 예외를 문자열 메시지로 만들지 않는다. 바이트코드 검사(forbiddenapis)가 못 보는 외부 라이브러리 예외까지 소스에서 잡는다. */
class NoHardcodedExceptionMessageRule : BusRule("no-hardcoded-exception-message") {

    override fun beforeVisitChildNodes(node: ASTNode, emit: Emit) {
        if (node.elementType != ElementType.CALL_EXPRESSION) return
        val call = node.psi as? KtCallExpression ?: return
        val name = call.calleeExpression?.text ?: return
        val hasReceiver = (call.parent as? KtDotQualifiedExpression)?.selectorExpression == call
        val literalArgument = call.valueArguments.any { it.getArgumentExpression() is KtStringTemplateExpression }
        val literalLambda = call.lambdaArguments.any {
            it.getLambdaExpression()?.bodyExpression?.statements?.lastOrNull() is KtStringTemplateExpression
        }
        val violated = when {
            name.endsWith("Exception") || name.endsWith("Error") -> literalArgument
            hasReceiver -> false
            name == "error" || name == "TODO" -> literalArgument
            name in PRECONDITIONS -> literalLambda
            else -> false
        }
        if (violated) emit(node.startOffset, MESSAGE, false)
    }

    companion object {
        private val PRECONDITIONS = setOf("require", "check", "requireNotNull", "checkNotNull")
        const val MESSAGE =
            "[bus:no-hardcoded-exception-message] 예외 메시지를 문자열로 쓰지 않는다. 모듈 Result enum에 코드를 추가하고 " +
                "BusinessException(XxxResult.Y)(4xx)나 ServiceException(XxxResult.Y)(5xx)를 던진다. 값은 log로 남긴다."
    }
}
