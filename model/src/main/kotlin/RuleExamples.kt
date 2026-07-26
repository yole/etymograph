package page.yole.etymograph

class RuleStepData(
    val result: String,
    val rule: Rule,
    val matchedBranches: Set<RuleBranch>?,
    val matchedInstructions: Set<RuleInstruction>?
)

class RuleExampleData(
    val link: Link,
    val steps: List<RuleStepData>,
    val branches: Set<RuleBranch>,
    val instructions: Set<RuleInstruction>
)

fun buildIntermediateSteps(link: Link, traceMatchedBranches: Boolean = true): List<RuleStepData> {
    var word = link.toEntity as Word
    val result = mutableListOf<RuleStepData>()
    val trace = if (traceMatchedBranches) RuleTrace() else null
    for (rule in link.rules) {
        val newWord = rule.apply(word, trace)
        val matchedBranches = trace?.findMatchedBranches(rule, word)?.ifEmpty {
            trace.findMatchedBranches(rule, newWord)
        }
        val matchedInstructions = trace?.findMatchedInstructions(rule, word)?.ifEmpty {
            trace.findMatchedInstructions(rule, newWord)
        }
        word = newWord
        result.add(RuleStepData(word.text, rule, matchedBranches, matchedInstructions))
    }
    return result
}

private fun findExampleForRule(link: Link, rule: Rule): RuleExampleData {
    val steps = buildIntermediateSteps(link)
    val theStep = steps.find { it.rule == rule }
    return RuleExampleData(
        link, steps,
        theStep?.matchedBranches ?: setOf(),
        theStep?.matchedInstructions ?: setOf()
    )
}

fun collectRuleExamples(rule: Rule): List<RuleExampleData> {
    val result = mutableListOf<RuleExampleData>()
    rule.fromLanguage.graph.findRuleExamples(rule).mapTo(result) { link -> findExampleForRule(link, rule) }
    return result
}
