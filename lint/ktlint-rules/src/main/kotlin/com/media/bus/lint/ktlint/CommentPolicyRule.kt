package com.media.bus.lint.ktlint

import com.pinterest.ktlint.rule.engine.core.api.ElementType
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import org.jetbrains.kotlin.com.intellij.lang.ASTNode

/** 주석 금지, KDoc은 함수·클래스에 한 줄만 허용. 진짜 예외 케이스는 `// 예외:`로만 남긴다. */
class CommentPolicyRule : BusRule("comment-policy") {

    override fun beforeVisitChildNodes(node: ASTNode, emit: Emit) {
        when (node.elementType) {
            ElementType.EOL_COMMENT, ElementType.BLOCK_COMMENT -> visitComment(node, emit)
            ElementType.KDOC -> visitKdoc(node, emit)
        }
    }

    private fun visitComment(node: ASTNode, emit: Emit) {
        if (ALLOWED_COMMENT.containsMatchIn(node.text)) return
        emit(node.startOffset, COMMENT_MESSAGE, true).ifAutocorrectAllowed { node.removeWithLine() }
    }

    private fun visitKdoc(node: ASTNode, emit: Emit) {
        val owner = node.treeParent
        if (owner == null || owner.elementType !in KDOC_OWNERS || owner.firstChildNode != node) {
            emit(node.startOffset, KDOC_OWNER_MESSAGE, true).ifAutocorrectAllowed { node.removeWithLine() }
            return
        }
        if ('\n' !in node.text) return
        emit(node.startOffset, KDOC_ONE_LINE_MESSAGE, true).ifAutocorrectAllowed {
            val summary = kdocSummary(node.text)
            if (summary.isEmpty()) node.removeWithLine() else node.replaceKdoc(summary)
        }
    }

    companion object {
        private val ALLOWED_COMMENT = Regex("^(//|/\\*)\\s*(예외:|language=|noinspection)")
        private val KDOC_OWNERS = setOf(
            ElementType.FUN,
            ElementType.CLASS,
            ElementType.OBJECT_DECLARATION,
            ElementType.SECONDARY_CONSTRUCTOR,
            ElementType.TYPEALIAS,
        )
        const val COMMENT_MESSAGE =
            "[bus:comment-policy] 주석을 쓰지 않는다. 의도는 이름·타입·테스트로 드러낸다. " +
                "꼭 필요한 예외 케이스만 `// 예외: <이유>` 한 줄로 남긴다(추가하려면 사용자 승인이 필요하다)."
        const val KDOC_ONE_LINE_MESSAGE = "[bus:comment-policy] KDoc은 한 줄 요약만 쓴다: /** 요약 */"
        const val KDOC_OWNER_MESSAGE =
            "[bus:comment-policy] KDoc은 함수·클래스에만 한 줄로 단다. 프로퍼티·파라미터·본문의 KDoc은 지운다."
    }
}
