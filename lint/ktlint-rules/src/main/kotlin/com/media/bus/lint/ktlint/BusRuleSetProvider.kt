package com.media.bus.lint.ktlint

import com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import com.pinterest.ktlint.rule.engine.core.api.RuleSetId

/** bus 규칙 묶음. 규칙별 적용 범위는 루트 `.editorconfig`의 `ktlint_bus_*` 값으로 정한다. */
class BusRuleSetProvider : RuleSetProviderV3(RuleSetId("bus")) {
    override fun getRuleProviders(): Set<RuleProvider> = setOf(
        RuleProvider { CommentPolicyRule() },
        RuleProvider { TestMethodNameRule() },
        RuleProvider { NoMethodDisplayNameRule() },
        RuleProvider { EntityFactoryNameRule() },
        RuleProvider { NoHardcodedExceptionMessageRule() },
    )
}
