package ru.yole.etymograph

fun Language.word(text: String, gloss: String? = null, pos: String? = null) = Word(-1, text, this, gloss, pos = pos)

fun createParseContext(
    fromLanguage: Language,
    toLanguage: Language,
    repo: GraphRepository?,
    vararg rules: Rule
) = RuleParseContext(repo ?: InMemoryGraphRepository(), fromLanguage, toLanguage) { ruleName ->
    repo?.ruleByName(ruleName)?.let { RuleRef.to(it) }
        ?: rules.find { rule -> rule.name == ruleName }?.let { RuleRef.to(it) }
        ?: throw RuleParseException("no such rule")
}

fun GraphRepository.rule(
    text: String,
    fromLanguage: Language,
    toLanguage: Language = fromLanguage,
    name: String = "q", addedCategories: String? = null
): Rule {
    return addRule(name, fromLanguage, toLanguage,
        Rule.parseBranches(text, createParseContext(fromLanguage, toLanguage, this)),
        addedCategories = addedCategories
    )
}

fun Rule.step(optional: Boolean = false) = RuleSequenceStep(this, optional)
fun RuleSequence.step() = RuleSequenceStep(this, false)
