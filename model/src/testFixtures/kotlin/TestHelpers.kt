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
        Rule.parseLogic(text, createParseContext(fromLanguage, toLanguage, this)),
        addedCategories = addedCategories
    )
}

fun Rule.step(alternative: Rule? = null, optional: Boolean = false, dispreferred: Boolean = false) =
    RuleSequenceStep(this, alternative, optional, dispreferred)
fun RuleSequence.step() = RuleSequenceStep(this, null, optional = false, dispreferred = false)

fun phoneme(grapheme: String, classes: String? = null): Phoneme {
    return Phoneme(-1, listOf(grapheme), null,
        classes?.split(' ')?.toSet() ?: defaultPhonemeClasses[grapheme]!!)
}

fun phoneme(graphemes: List<String>, sound: String? = null, classes: String? = null): Phoneme {
    return Phoneme(-1, graphemes, sound, classes?.split(' ')?.toSet() ?: emptySet())
}

val Rule.firstInstruction: RuleInstruction
    get() {
        val logic = this.logic
        return when (logic) {
            is MorphoRuleLogic -> logic.branches[0].instructions[0]
            is SpeRuleLogic -> logic.instructions[0]
        }
    }

val Rule.firstCondition get() = (logic as MorphoRuleLogic).branches[0].condition
