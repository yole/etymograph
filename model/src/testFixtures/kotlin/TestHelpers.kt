package page.yole.etymograph

fun Language.word(
    text: String,
    gloss: String? = null,
    fullGloss: String? = null,
    pos: String? = null,
    classes: List<String> = emptyList(),
    syllabographic: Boolean = false
) = graph.addWord(text, this, gloss, fullGloss = fullGloss, pos = pos, classes = classes, syllabographic = syllabographic)

fun Language.rule(
    text: String,
    toLanguage: Language = this,
    name: String = "q",
    addedCategories: String? = null,
    fromPOS: String? = null,
    toPOS: String? = null
): Rule {
    return graph.addRule(name, this, toLanguage,
        Rule.parseLogic(text, RuleParseContext.of(this, toLanguage)),
        addedCategories = addedCategories,
        fromPOS = fromPOS?.let { listOf(it) } ?: emptyList(),
        toPOS = toPOS
    )
}

fun Rule.step(alternative: Rule? = null, optional: Boolean = false, dispreferred: Boolean = false) =
    RuleSequenceStep(this, alternative, optional, dispreferred)
fun RuleSequence.step() = RuleSequenceStep(this, null, optional = false, dispreferred = false)

fun phoneme(grapheme: String, classes: String? = null): Phoneme {
    return Phoneme(-1, listOf(grapheme), sound = null,
        classes = classes?.split(' ')?.toSet() ?: defaultPhonemeClasses[grapheme] ?: emptySet())
}

fun phoneme(graphemes: List<String>, sound: String? = null, classes: String? = null): Phoneme {
    return Phoneme(-1, graphemes, sound = sound, classes = classes?.split(' ')?.toSet() ?: emptySet())
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

operator fun Rule.invoke(text: String): String = apply(fromLanguage.word(text)).text

fun Language.withGrammaticalCategory(name: String, pos: String, vararg values: Pair<String, String>): Language {
    grammaticalCategories.add(WordCategory(name, pos.split(','), values.map { WordCategoryValue(it.first, it.second) }))
    return this
}
