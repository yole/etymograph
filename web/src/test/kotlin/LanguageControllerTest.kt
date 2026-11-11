package page.yole.etymograph.web

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import page.yole.etymograph.*
import page.yole.etymograph.web.controllers.LanguageController

class LanguageControllerTest {
    private lateinit var fixture: QTestFixture
    private lateinit var languageController: LanguageController

    @Before
    fun setup() {
        fixture = QTestFixture()
        languageController = LanguageController()
    }

    @Test
    fun grammaticalCategories() {
        val parameters = LanguageController.UpdateLanguageParameters(
            grammaticalCategories = "Number (N, ADJ): Singular (SG), Plural (PL), Dual (DU), Collective Plural (PPL)\nCase (N): Nominative (NOM), Genitive (GEN)"
        )
        languageController.updateLanguage(fixture.graph, "q", parameters)

        val categories = fixture.q.grammaticalCategories
        assertEquals(2, categories.size)
        val number = categories[0]
        assertEquals("Number", number.name)
        assertEquals("ADJ", number.pos[1])
        assertEquals(4, number.values.size)
        assertEquals("Singular", number.values[0].name)
        assertEquals("SG", number.values[0].abbreviation)

        val vm = languageController.language(fixture.graph, "q")
        assertEquals(vm.grammaticalCategories, parameters.grammaticalCategories)
    }

    @Test
    fun wordClassesOptionalAbbreviation() {
        val parameters = LanguageController.UpdateLanguageParameters(
            wordClasses = "Plural type (N): class-plural-rim"
        )
        languageController.updateLanguage(fixture.graph, "q", parameters)

        val categories = fixture.q.wordClasses
        assertEquals(1, categories.size)
        val pluralType = categories[0]
        assertEquals(1, pluralType.values.size)
        assertEquals("class-plural-rim", pluralType.values[0].name)
        assertEquals("class-plural-rim", pluralType.values[0].abbreviation)

        val vm = languageController.language(fixture.graph, "q")
        assertEquals(vm.wordClasses, parameters.wordClasses)
    }

    @Test
    fun renameWordClassValueUpdatesAllWordsInLanguage() {
        languageController.updateLanguage(fixture.graph, "q", LanguageController.UpdateLanguageParameters(
            wordClasses = "Plural type (N): class-plural-rim, class-plural-li\nGender (N): Animate (AN)"
        ))
        val words = listOf("class-plural-rim", "CLASS-PLURAL-RIM").mapIndexed { index, cls ->
            fixture.graph.addWord("word$index", fixture.q, null, classes = listOf("AN", cls))
        }
        val unrelated = fixture.graph.addWord("other", fixture.q, null, classes = listOf("class-plural-li"))
        val otherLanguage = fixture.graph.addWord("other", fixture.ce, null, classes = listOf("class-plural-rim"))

        languageController.renameWordClassValue(fixture.graph, "q",
            LanguageController.RenameWordClassValueParams("class-plural-rim", "  class-plural-ron  "))

        assertEquals("class-plural-ron", fixture.q.wordClasses[0].values[0].name)
        assertEquals("class-plural-ron", fixture.q.wordClasses[0].values[0].abbreviation)
        words.forEach { assertEquals(listOf("AN", "class-plural-ron"), it.classes) }
        assertEquals(listOf("class-plural-li"), unrelated.classes)
        assertEquals(listOf("class-plural-rim"), otherLanguage.classes)
        assertEquals("Plural type (N): class-plural-ron, class-plural-li\nGender (N): Animate (AN)",
            languageController.language(fixture.graph, "q").wordClasses)
    }

    @Test
    fun renameWordClassValueWithAbbreviation() {
        fixture.q.wordClasses.add(WordCategory("Gender", listOf("N"), listOf(WordCategoryValue("Animate", "AN"))))
        val word = fixture.graph.addWord("word", fixture.q, null, classes = listOf("AN"))

        languageController.renameWordClassValue(fixture.graph, "q",
            LanguageController.RenameWordClassValueParams("AN", "Living (AN)"))
        assertEquals("Living", fixture.q.wordClasses.single().values.single().name)
        assertEquals(listOf("AN"), word.classes)

        languageController.renameWordClassValue(fixture.graph, "q",
            LanguageController.RenameWordClassValueParams("AN", "Inanimate (IN)"))
        assertEquals("Inanimate", fixture.q.wordClasses.single().values.single().name)
        assertEquals("IN", fixture.q.wordClasses.single().values.single().abbreviation)
        assertEquals(listOf("IN"), word.classes)
    }

    @Test
    fun renameWordClassValueUpdatesNestedConditionsInRulesFromLanguage() {
        for (language in listOf(fixture.q, fixture.ce)) {
            language.wordClasses.add(WordCategory("Gender", listOf("N"), listOf(
                WordCategoryValue("Animate", "AN"), WordCategoryValue("Inanimate", "IN")
            )))
        }
        fun addRule(name: String, from: Language, to: Language, condition: String): Rule {
            val logic = Rule.parseLogic("$condition:\n - no change", RuleParseContext.of(from, to))
            return fixture.graph.addRule(name, from, to, logic)
        }
        val nested = addRule("nested", fixture.q, fixture.q,
            "word is AN or (word is not AN and not (word is an or word is IN))")
        val outgoing = addRule("outgoing", fixture.q, fixture.ce, "word is AN")
        val incoming = addRule("incoming", fixture.ce, fixture.q, "word is AN")
        val unrelated = addRule("unrelated", fixture.q, fixture.q, "word is IN")
        val word = fixture.graph.addWord("word", fixture.q, null, classes = listOf("AN"))

        languageController.renameWordClassValue(fixture.graph, "q",
            LanguageController.RenameWordClassValueParams("AN", "Living (LIVE)"))

        assertEquals("word is LIVE or (word is not LIVE and not (word is LIVE or word is IN))",
            (nested.logic as MorphoRuleLogic).branches.single().condition.toEditableText())
        assertEquals("word is LIVE", (outgoing.logic as MorphoRuleLogic).branches.single().condition.toEditableText())
        assertEquals("word is AN", (incoming.logic as MorphoRuleLogic).branches.single().condition.toEditableText())
        assertEquals("word is IN", (unrelated.logic as MorphoRuleLogic).branches.single().condition.toEditableText())
        assertEquals("word", nested.apply(word).text)
    }

    @Test
    fun renameWordClassValuePreservesConditionalSoundRuleBehavior() {
        fixture.q.wordClasses.add(WordCategory("Gender", listOf("N"), listOf(
            WordCategoryValue("Animate", "AN"), WordCategoryValue("Inanimate", "IN")
        )))
        for (sound in listOf("a", "i")) {
            fixture.graph.addPhoneme(fixture.q, listOf(sound), sound = null, classes = emptySet())
        }
        val logic = Rule.parseLogic("* a > i if word is AN and not (word is IN)",
            RuleParseContext.of(fixture.q, fixture.q))
        val rule = fixture.graph.addRule("conditional sound rule", fixture.q, fixture.q, logic)
        val word = fixture.graph.addWord("a", fixture.q, null, classes = listOf("AN"))
        assertEquals("i", rule.apply(word).text)

        languageController.renameWordClassValue(fixture.graph, "q",
            LanguageController.RenameWordClassValueParams("AN", "Living (LIVE)"))

        assertEquals("word is LIVE and not (word is IN)",
            (logic as SpeRuleLogic).instructions.single().condition!!.toEditableText())
        assertEquals("i", rule.apply(word).text)
    }

    @Test
    fun renameWordClassValueUpdatesConditionsInAllInstructionPositions() {
        fixture.q.wordClasses.add(WordCategory("Gender", listOf("N"), listOf(WordCategoryValue("Animate", "AN"))))
        val context = RuleParseContext.of(fixture.q, fixture.q)
        val instructions = List(5) { SpeInstruction.parse("a > i if word is not AN", context, "Keep AN comment") }
        val literalInstruction = RuleInstruction(InstructionType.Append, "AN", null)
        fixture.graph.addRule("morpho", fixture.q, fixture.q, MorphoRuleLogic(
            listOf(instructions[0]),
            listOf(RuleBranch(OtherwiseCondition, listOf(instructions[1], literalInstruction))),
            listOf(instructions[2])
        ))
        fixture.graph.addRule("spe", fixture.q, fixture.q,
            SpeRuleLogic(listOf(instructions[3]), listOf(instructions[4])))

        languageController.renameWordClassValue(fixture.graph, "q",
            LanguageController.RenameWordClassValueParams("AN", "Living (LIVE)"))

        for (instruction in instructions) {
            assertEquals("word is not LIVE", instruction.condition!!.toEditableText())
            assertEquals("Keep AN comment", instruction.comment)
        }
        assertEquals("AN", literalInstruction.arg)
    }

    @Test
    fun invalidWordClassRenameDoesNotChangeLanguageOrWords() {
        fixture.q.pos.add(WordCategoryValue("Noun", "N"))
        fixture.q.wordClasses.add(WordCategory("Gender", listOf("N"), listOf(WordCategoryValue("Animate", "AN"))))
        fixture.q.wordClasses.add(WordCategory("Declension", listOf("N"), listOf(WordCategoryValue("First", "I"))))
        val word = fixture.graph.addWord("word", fixture.q, null, classes = listOf("AN"))
        val condition = WordClassCondition("AN", false)
        fixture.graph.addRule("q-class", fixture.q, fixture.q, MorphoRuleLogic(
            emptyList(), listOf(RuleBranch(condition, emptyList())), emptyList()
        ))

        for (value in listOf("I", "First (i)", "N", "Noun (n)")) {
            val abbreviation = if ('(' in value) value.substringAfter('(').removeSuffix(")") else value
            assertBadRequest("Duplicate word class or part of speech abbreviation '$abbreviation'") {
                languageController.renameWordClassValue(fixture.graph, "q",
                    LanguageController.RenameWordClassValueParams("AN", value))
            }
        }
        for (value in listOf("", "  ", "One, Two", "One\nTwo", "Gender: One")) {
            assertBadRequest("Enter a single word class value") {
                languageController.renameWordClassValue(fixture.graph, "q",
                    LanguageController.RenameWordClassValueParams("AN", value))
            }
        }
        assertBadRequest("Word class abbreviation must not contain whitespace") {
            languageController.renameWordClassValue(fixture.graph, "q",
                LanguageController.RenameWordClassValueParams("AN", "New name (A N)"))
        }
        assertBadRequest("Unknown word class 'missing'") {
            languageController.renameWordClassValue(fixture.graph, "q",
                LanguageController.RenameWordClassValueParams("missing", "IN"))
        }
        assertEquals("Animate", fixture.q.wordClasses[0].values.single().name)
        assertEquals("AN", fixture.q.wordClasses[0].values.single().abbreviation)
        assertEquals(listOf("AN"), word.classes)
        assertEquals("AN", condition.wordClass)
    }

    @Test
    fun wordClassAndPartOfSpeechAbbreviationsMustBeUnique() {
        assertBadRequest("Duplicate word class or part of speech abbreviation 'N'") {
            languageController.updateLanguage(fixture.graph, "q", LanguageController.UpdateLanguageParameters(
                pos = "Noun (N), Name (N)"
            ))
        }
        assertBadRequest("Duplicate word class or part of speech abbreviation 'AN'") {
            languageController.updateLanguage(fixture.graph, "q", LanguageController.UpdateLanguageParameters(
                wordClasses = "Gender (N): Animate (AN)\nAnimacy (N): Inanimate (AN)"
            ))
        }
        assertBadRequest("Duplicate word class or part of speech abbreviation 'N'") {
            languageController.updateLanguage(fixture.graph, "q", LanguageController.UpdateLanguageParameters(
                pos = "Noun (N)",
                wordClasses = "Declension (N): First declension (N)"
            ))
        }
    }

    @Test
    fun diphthongs() {
        val parameters = LanguageController.UpdateLanguageParameters(
            diphthongs = ""
        )
        languageController.updateLanguage(fixture.graph, "q", parameters)

        assertEquals(0, fixture.q.diphthongs.size)
    }

    @Test
    fun phonotacticsRules() {
        val rule = fixture.graph.addRule("q-phono", fixture.q, fixture.q,
            MorphoRuleLogic.empty())

        val parameters = LanguageController.UpdateLanguageParameters(
            phonotacticsRuleName = "q-phono"
        )
        languageController.updateLanguage(fixture.graph, "q", parameters)
        assertEquals(rule.id, fixture.q.phonotacticsRule?.resolve()?.id)

        val languageVM = languageController.language(fixture.graph, "q")
        assertEquals(rule.id, languageVM.phonotacticsRuleId)
        assertEquals("q-phono", languageVM.phonotacticsRuleName)
    }

    @Test
    fun copyPhonemes() {
        fixture.graph.addPhoneme(
            language = fixture.q,
            graphemes = listOf("ā"),
            simplifiedGrapheme = "a",
            sound = null,
            classes = setOf("open", "back", "vowel")
        )
        languageController.addLanguage(fixture.graph, LanguageController.UpdateLanguageParameters(
            "Sindarin", "S"
        ))
        languageController.copyPhonemes(fixture.graph, "S", LanguageController.CopyPhonemesParams("q"))
        val s = fixture.graph.languageByShortName("S")!!
        assertEquals("a", s.phonemes.single().simplifiedGrapheme)
    }

    @Test
    fun inputAssistSkipsPhonemesWithoutGraphemes() {
        val phonemeWithoutGraphemes = fixture.graph.addPhoneme(
            fixture.q,
            listOf("x"),
            sound = null,
            classes = emptySet()
        )
        fixture.graph.addPhoneme(fixture.q, listOf("ä"), sound = null, classes = emptySet())
        phonemeWithoutGraphemes.graphemes = emptyList()

        val viewModel = languageController.inputAssist(fixture.graph)

        assertEquals(listOf("q"), viewModel.graphemes.single { it.text == "ä" }.languages)
    }

    @Test
    fun inputAssistAllGraphemes() {
        val phoneme = fixture.graph.addPhoneme(
            fixture.q,
            listOf("á", "ā"),
            sound = null,
            classes = emptySet()
        )
        val viewModel = languageController.inputAssist(fixture.graph)

        assertEquals(listOf("á", "ā", "ˈ"), viewModel.graphemes.map { it.text })
    }

    @Test
    fun stressTypes() {
        val parameters = LanguageController.UpdateLanguageParameters(
            accentTypes = listOf(AccentType.Circumflex.name)
        )
        languageController.updateLanguage(fixture.graph, "q", parameters)
        assertEquals(setOf(AccentType.Circumflex), fixture.q.accentTypes)
        assertEquals(listOf(AccentType.Circumflex.name),
            languageController.language(fixture.graph, "q").accentTypes)
    }
}
