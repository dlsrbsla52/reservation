package com.media.bus.lint.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode

typealias Emit = (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision

/** bus 규칙 공통 바탕. 규칙 ID는 `bus:<id>`다. */
abstract class BusRule(id: String) :
    Rule(ruleId = RuleId("bus:$id"), about = About(maintainer = "bus")),
    RuleAutocorrectApproveHandler {

    abstract override fun beforeVisitChildNodes(node: ASTNode, emit: Emit)
}
