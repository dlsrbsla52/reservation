package com.media.bus.lint.ktlint

import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.TokenType
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.LeafElement
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtPsiFactory

private fun ASTNode.prevLeafNode(): ASTNode? = PsiTreeUtil.prevLeaf(psi)?.node

private fun ASTNode.nextLeafNode(): ASTNode? = PsiTreeUtil.nextLeaf(psi)?.node

private fun ASTNode?.whitespaceOrNull(): ASTNode? = this?.takeIf { it.elementType == TokenType.WHITE_SPACE }

/** 노드를 지우고, 혼자 한 줄을 차지했다면 그 줄도 없앤다. 빈 줄은 앞뒤 중 많은 쪽만 남기고 들여쓰기는 다음 줄을 따른다. */
internal fun ASTNode.removeWithLine() {
    val prevWs = prevLeafNode().whitespaceOrNull()
    val nextWs = nextLeafNode().whitespaceOrNull()
    when {
        prevWs != null && '\n' in prevWs.text && nextWs != null && nextWs.text.startsWith("\n") -> {
            val newlines = maxOf(prevWs.text.count { it == '\n' }, nextWs.text.count { it == '\n' })
            (prevWs as LeafElement).rawReplaceWithText("\n".repeat(newlines) + nextWs.text.substringAfterLast('\n'))
            nextWs.treeParent.removeChild(nextWs)
        }
        prevWs != null && '\n' !in prevWs.text -> prevWs.treeParent.removeChild(prevWs)
        nextWs != null && (prevWs == null || '\n' !in nextWs.text) -> nextWs.treeParent.removeChild(nextWs)
    }
    treeParent.removeChild(this)
}

/** KDoc 노드를 `/** summary */` 한 줄로 바꾼다. */
internal fun ASTNode.replaceKdoc(summary: String) {
    val function = KtPsiFactory(psi.project).createFunction("/** $summary */\nfun placeholder() {}")
    val kdoc = requireNotNull(function.docComment).node
    treeParent.replaceChild(this, kdoc)
}

private val BULLET = Regex("^([-*]|\\d+\\.)\\s+")

/** 여러 줄 KDoc에서 첫 문단의 첫 문장(제목이면 제목)을 뽑는다. 태그만 있으면 빈 문자열이다. */
internal fun kdocSummary(kdocText: String): String {
    val lines = kdocText.removePrefix("/**").removeSuffix("*/").lines()
        .map { it.trim().removePrefix("*").trim() }
    val paragraph = mutableListOf<String>()
    for (line in lines) {
        when {
            line.isEmpty() -> if (paragraph.isNotEmpty()) break
            line.startsWith("@") || line.startsWith("```") -> break
            BULLET.containsMatchIn(line) -> {
                if (paragraph.isEmpty()) paragraph += line.replace(BULLET, "")
                break
            }
            line.startsWith("#") -> {
                if (paragraph.isEmpty()) paragraph += line.trimStart('#').trim()
                break
            }
            else -> paragraph += line
        }
    }
    val text = paragraph.joinToString(" ").replace(Regex("\\s+"), " ").trim()
    val sentenceEnd = Regex("\\.(\\s|$)").find(text)
    return if (sentenceEnd == null) text else text.substring(0, sentenceEnd.range.first + 1)
}
