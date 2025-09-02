package ru.yole.etymograph

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ru.yole.etymograph.JsonGraphRepository.Companion.ruleBranchesFromSerializedFormat
import ru.yole.etymograph.JsonGraphRepository.Companion.ruleInstructionFromSerializedFormat
import ru.yole.etymograph.JsonGraphRepository.Companion.ruleToSerializedFormat
import ru.yole.etymograph.JsonGraphRepository.Companion.toSerializedFormat

class RuleTest : QBaseTest() {
    private val dummyRule = parseRule(q, q, "- append 'a'")
    private val dummyContext = RuleApplyContext(dummyRule, null, emptyRepo, null)

    lateinit var repo: GraphRepository

    @Before
    fun setup() {
        repo = repoWithQ()
    }

    @Test
    fun conditions() {
        val c = LeafRuleCondition(ConditionType.EndsWith, v, null, false, null)
        assertTrue(c.matches(Word(0, "parma", q), emptyRepo))
        assertFalse(c.matches(Word(0, "formen", q), emptyRepo))
    }

    @Test
    fun instructions() {
        val i = PrependAppendInstruction(InstructionType.Append, q, "'a'", null)
        assertEquals("parma", i.apply(q.word("parm"), dummyContext).text)
    }

    @Test
    fun rule() {
        val v = PhonemeClass("e", listOf("e", "ë"))
        val c = LeafRuleCondition(ConditionType.EndsWith, v, null, false, null)
        val i2 = PrependAppendInstruction(InstructionType.Append, q, "'i'", null)
        val r = RuleBranch(c, listOf(i2))

        assertTrue(r.matches(Word(0, "lasse", q), emptyRepo))
        val word = q.word("atan")
        assertEquals("atani", r.apply(dummyRule, word, word, emptyRepo).text)
    }

    @Test
    fun conditionParse() {
        val c = LeafRuleCondition.parse(ParseBuffer("word ends with 'eë'"), q) as LeafRuleCondition
        assertEquals(ConditionType.EndsWith, c.type)
        assertEquals("eë", c.parameter)

        val c2 = LeafRuleCondition.parse(ParseBuffer("word ends with a vowel"), q) as LeafRuleCondition
        assertEquals(ConditionType.EndsWith, c2.type)
        assertEquals(v, c2.phonemeClass)
    }

    @Test
    fun conditionParseOr() {
        val c = RuleCondition.parse(ParseBuffer("word ends with 'e' or word ends with 'ë'"), q)
        assertTrue(c is OrRuleCondition)
        val l1 = (c as OrRuleCondition).members[0] as LeafRuleCondition
        assertEquals(ConditionType.EndsWith, l1.type)
        assertEquals("e", l1.parameter)
    }

    @Test
    fun conditionParseAnd() {
        val c = RuleCondition.parse(ParseBuffer("word ends with a vowel and word ends with 'a'"), q)
        assertTrue(c is AndRuleCondition)
        val l1 = (c as AndRuleCondition).members[0] as LeafRuleCondition
        assertEquals(ConditionType.EndsWith, l1.type)
        assertEquals("vowel", l1.phonemeClass?.name)
    }

    @Test
    fun parseParentheses() {
        val text = "(word ends with vowel and word ends with 'a') or word ends with 'c'"
        val c = RuleCondition.parse(ParseBuffer(text), q)
        assertTrue(c is OrRuleCondition)
        val a = (c as OrRuleCondition).members[0] as AndRuleCondition
        val l1 = a.members[0] as LeafRuleCondition
        assertEquals(ConditionType.EndsWith, l1.type)
        assertEquals("vowel", l1.phonemeClass?.name)
        assertEquals(text, c.toEditableText())
    }

    @Test
    fun andToString() {
        val text = "(word ends with vowel or word ends with 'c') and word begins with 'c'"
        val c = RuleCondition.parse(ParseBuffer(text), q)
        assertEquals(text, c.toEditableText())
    }

    @Test
    fun instructionParse() {
        val i = RuleInstruction.parse("- sound disappears", q.parseContext())
        assertEquals(InstructionType.SoundDisappears, i.type)

        val i2 = RuleInstruction.parse("- append 'a'", q.parseContext())
        assertEquals(InstructionType.Append, i2.type)
        assertEquals("'a'", i2.arg)
    }

    @Test
    fun branchParse() {
        val b = RuleBranch.parse(
            LineBuffer(
                """
            word ends with 'e':
            - append 'a'
        """.trimIndent()
            ), q.parseContext()
        )
        assertEquals("e", (b.condition as LeafRuleCondition).parameter)
        assertEquals("'a'", b.instructions[0].arg)
    }

    @Test
    fun branchParseComment() {
        val branchText = """
            $ this is a comment
            $ second line
            word ends with 'e':
             - append 'a'
        """.trimIndent()
        val b = RuleBranch.parse(LineBuffer(branchText), q.parseContext())
        assertEquals("this is a comment\nsecond line", b.comment)
        assertEquals("e", (b.condition as LeafRuleCondition).parameter)
        assertEquals("'a'", b.instructions[0].arg)

        assertEquals(branchText, b.toEditableText(repo))
    }

    @Test
    fun ruleParse() {
        val branches = Rule.parseBranches(
            """
            word ends with 'e':
            - append 'a'
            word ends with 'i':
            - append 'r'
        """.trimIndent(), q.parseContext()
        ).branches
        assertEquals(2, branches.size)
        assertEquals(1, branches[0].instructions.size)
        assertEquals(1, branches[1].instructions.size)
    }

    @Test
    fun ruleParseComment() {
        val branches = Rule.parseBranches(
            """
            $ this is e
            word ends with 'e':
            - append 'a'
            $ this is a
            word ends with 'i':
            - append 'r'
        """.trimIndent(), q.parseContext()
        ).branches
        assertEquals(2, branches.size)
        assertEquals("this is e", branches[0].comment)
        assertEquals("this is a", branches[1].comment)
    }

    @Test
    fun ruleParseWithoutConditions() {
        val branches = Rule.parseBranches(
            """
            - append 'lye'
        """.trimIndent(), q.parseContext()
        ).branches
        assertEquals(1, branches.size)
        assertEquals(1, branches[0].instructions.size)
        assertTrue(branches[0].matches(Word(0, "abc", q), emptyRepo))
    }

    @Test
    fun ruleParseOtherwise() {
        val branches = Rule.parseBranches(
            """
            word ends with 'e':
            - append 'a'
            otherwise:
            - append 'r'
        """.trimIndent(), q.parseContext()
        ).branches
        assertEquals(2, branches.size)
        assertEquals(1, branches[0].instructions.size)
        assertEquals(OtherwiseCondition, branches[1].condition)
    }

    @Test
    fun ruleParseSPE() {
        val rule = parseRule(q, q, " * d -> l / #_")
        assertEquals(1, rule.logic.branches.size)
        val speInstruction = rule.logic.branches[0].instructions.single() as SpeInstruction
        assertEquals("l", (speInstruction.pattern.after.single() as SpeLiteralNode).text)
        assertEquals("* d > l / #_", rule.toEditableText(repo))
    }

    @Test
    fun wordIsUnknownClass() {
        assertThrows("Unknown word class 'm'", RuleParseException::class.java) {
            Rule.parseBranches("word is m:\n- no change", q.parseContext())
        }
    }

    @Test
    fun wordIsPOS() {
        q.pos = mutableListOf(WordCategoryValue("Noun", "N"), WordCategoryValue("Verb", "V"))
        val rule = repo.rule("word is V and word ends with 'ōn':\n- change ending to 'ōjan'\notherwise:\n- no change", q)
        val baseWord = repo.addWord("bugōn-", language = q, pos = "V")
        assertEquals("bugōjan", rule.apply(baseWord, repo).text)
    }

    @Test
    fun wordIsInheritFromVariant() {
        val repo = InMemoryGraphRepository()
        val oe = Language("Old English", "OE").also { repo.addLanguage(it) }
        oe.wordClasses =
            mutableListOf(WordCategory("stem class", listOf("N"), listOf(WordCategoryValue("o-stem", "o-stem"))))
        val rule = repo.rule("word is o-stem:\n- append 'es'", oe)
        val baseWord = repo.addWord("mann", language = oe, classes = listOf("o-stem"))
        val variant = repo.addWord("monn", language = oe)
        repo.addLink(variant, baseWord, Link.Variation)
        val result = rule.apply(variant, repo)
        assertEquals("monnes", result.text)
    }

    @Test
    fun wordIsInheritFromVariantWithPreActions() {
        val repo = InMemoryGraphRepository()
        val oe = Language("Old English", "OE").also { repo.addLanguage(it) }
        oe.wordClasses = mutableListOf(
            WordCategory(
                "stem class", listOf("N"),
                listOf(WordCategoryValue("o-stem", "o-stem"), WordCategoryValue("strong", "strong"))
            )
        )
        val rule = repo.rule("- mark word as strong\nword is o-stem:\n- append 'es'", oe)
        val baseWord = repo.addWord("mann", language = oe, classes = listOf("o-stem"))
        val variant = repo.addWord("monn", language = oe)
        repo.addLink(variant, baseWord, Link.Variation)
        val result = rule.apply(variant, repo)
        assertEquals("monnes", result.text)
    }

    @Test
    fun wordIsInheritFromVariantWithApplyRule() {
        val repo = InMemoryGraphRepository()
        val oe = Language("Old English", "OE").also { repo.addLanguage(it) }
        oe.wordClasses = mutableListOf(
            WordCategory(
                "stem class", listOf("N"),
                listOf(WordCategoryValue("o-stem", "o-stem"), WordCategoryValue("strong", "strong"))
            )
        )
        repo.rule("- mark word as strong", oe, name = "oe-stem-class")
        val rule = repo.rule("- apply rule 'oe-stem-class'\nword is o-stem:\n- append 'es'", oe)
        val baseWord = repo.addWord("mann", language = oe, classes = listOf("o-stem"))
        val variant = repo.addWord("monn", language = oe)
        repo.addLink(variant, baseWord, Link.Variation)
        val result = rule.apply(variant, repo)
        assertEquals("monnes", result.text)
    }

    @Test
    fun wordIsInheritFromCompound() {
        val repo = InMemoryGraphRepository()
        val oe = Language("Old English", "OE").also { repo.addLanguage(it) }
        oe.wordClasses = mutableListOf(
            WordCategory(
                "stem class", listOf("N"),
                listOf(WordCategoryValue("o-stem", "o-stem"), WordCategoryValue("strong", "strong"))
            )
        )
        val rule = repo.rule("- mark word as strong\nword is o-stem:\n- append 'es'", oe)
        val baseWord = repo.addWord("mann", language = oe, classes = listOf("o-stem"))
        val prefix = repo.addWord("sæ", language = oe)
        val compoundWord = repo.addWord("sæmann", language = oe)
        repo.createCompound(compoundWord, listOf(prefix, baseWord), headIndex = 1)
        val result = rule.apply(compoundWord, repo)
        assertEquals("sæmannes", result.text)
    }

    @Test
    fun applyRuleToCompound() {
        val repo = InMemoryGraphRepository()
        val on = Language("Old Norse", "ON").also { repo.addLanguage(it) }
        val rule = repo.rule("word ends with 'r':\n- change ending to 'ar'", name = "on-nom-pl")
        val madr = repo.addWord("maðr", language = on, gloss = "man")
        val menn = repo.addWord("menn", language = on)
        repo.addLink(menn, madr, Link.Derived, listOf(rule))

        val far = repo.addWord("go",  language = on, gloss = "go")
        val farmadr = repo.addWord("farmaðr", language = on)
        repo.createCompound(farmadr, listOf(far, madr), headIndex = 1)

        val result = rule.apply(farmadr, repo)
        assertEquals("farmenn", result.text)
    }

    @Test
    fun postInstructions() {
        val text = """
            word ends with 'e':
             - append 'a'
            
            word ends with 'i':
             - append 'r'
            
             = append 'e'
        """.trimIndent()
        val rule = repo.rule(text, q)
        assertEquals(1, rule.logic.postInstructions.size)
        assertEquals("laureae", applyRule(rule, q.word("laure")))
        assertEquals("lomire", applyRule(rule, q.word("lomi")))
        assertEquals(text, rule.toEditableText(repo))
    }

    @Test
    fun beginsWith() {
        val rule = parseRule(q, q, "word begins with 'b':\n- prepend 'm'")
        assertEquals("mbar", rule.apply(q.word("bar"), emptyRepo).text)
    }

    @Test
    fun applySoundRule() {
        val soundRule = parseRule(
            q, q, """
            sound is 'a':
            - new sound is 'á'
        """.trimIndent()
        )
        val applySoundRuleInstruction = ApplySoundRuleInstruction(q, RuleRef.to(soundRule), "first vowel", null)
        assertEquals("lásse", applySoundRuleInstruction.apply(q.word("lasse"), dummyContext).text)
    }

    @Test
    fun parseApplySoundRule() {
        val soundRule = parseRule(
            q, q, """
            sound is 'a':
            - new sound is 'á'
        """.trimIndent(), name = "q-lengthen"
        )
        val parseContext = q.parseContext(null, soundRule)
        val applySoundRule = Rule(
            -1, "q-lengthen-first", q, q, Rule.parseBranches(
                """
            - apply sound rule 'q-lengthen' to first vowel
        """.trimIndent(), parseContext
            )
        )
        assertEquals("lásse", applySoundRule.apply(q.word("lasse"), emptyRepo).text)
    }

    @Test
    fun applySoundRuleToSound() {
        val soundRule = parseRule(
            q, q, """
            sound is 'a':
            - new sound is 'á'
        """.trimIndent(), name = "q-lengthen-sound"
        )
        val parseContext = q.parseContext(null, soundRule)
        val applySoundRule = Rule(
            -1, "q-lengthen", q, q, Rule.parseBranches(
                """
            - apply sound rule 'q-lengthen-sound' to first sound
        """.trimIndent(), parseContext
            )
        )
        assertEquals("áire", applySoundRule.apply(q.word("aire"), emptyRepo).text)
        assertEquals(
            "apply sound rule 'q-lengthen-sound' to first sound",
            applySoundRule.logic.branches[0].instructions[0].toEditableText(repo)
        )
    }

    @Test
    fun applySoundRuleOrtho() {
        q.phonemes = listOf(
            phoneme(listOf("c", "k"), "k", ""),
            phoneme(listOf("ch"), "x", "")
        )
        val soundRule = parseRule(
            q, q, """
            sound is 'k':
            - new sound is 'x'
        """.trimIndent(), name = "q-lengthen-sound"
        )
        val parseContext = q.parseContext(null, soundRule)
        val applySoundRule = Rule(
            -1, "q-lengthen", q, q, Rule.parseBranches(
                """
            - apply sound rule 'q-lengthen-sound' to first sound
        """.trimIndent(), parseContext
            )
        )
        assertEquals("chaered", applySoundRule.apply(q.word("caered"), emptyRepo).text)
    }

    @Test
    fun applySoundRuleCase() {
        q.phonemes = listOf(phoneme(listOf("c", "k"), "k", ""), phoneme(listOf("ch"), "x", ""))
        val soundRule = parseRule(
            q, q, """
            sound is 'p':
            - new sound is 'ph'
        """.trimIndent(), name = "q-lengthen-sound"
        )
        val parseContext = q.parseContext(null, soundRule)
        val applySoundRule = Rule(
            -1, "q-lengthen", q, q, Rule.parseBranches(
                """
            - apply sound rule 'q-lengthen-sound' to first sound
        """.trimIndent(), parseContext
            )
        )
        assertEquals("pherian", applySoundRule.apply(q.word("Perian"), emptyRepo).text)
    }

    @Test
    fun soundRuleToEditableText() {
        val soundRule = parseRule(
            q, q, """
            sound is 'a':
            - new sound is 'á'
        """.trimIndent()
        )
        val applySoundRuleInstruction = ApplySoundRuleInstruction(q, RuleRef.to(soundRule), "first vowel", null)
        assertEquals("apply sound rule 'q' to first vowel", applySoundRuleInstruction.toEditableText(repo))
    }

    @Test
    fun syllableMatcher() {
        val condition =
            RuleCondition.parse(ParseBuffer("second to last syllable contains long vowel"), q) as SyllableRuleCondition
        assertEquals(-2, condition.index)
        assertEquals("long vowel", condition.phonemePattern.phonemeClass!!.name)
        assertTrue(condition.matches(q.word("andúna"), emptyRepo))
        assertFalse(condition.matches(q.word("anca"), emptyRepo))
        assertEquals("second to last syllable contains long vowel", condition.toEditableText())
        assertFalse(condition.refersToPhoneme(q.phonemes.find { "á" in it.graphemes }!!))
    }

    @Test
    fun syllableMatcherSpecific() {
        val condition =
            RuleCondition.parse(ParseBuffer("second to last syllable contains 'ú'"), q) as SyllableRuleCondition
        assertEquals(-2, condition.index)
        assertEquals("ú", condition.phonemePattern.literal!!)
        assertTrue(condition.matches(q.word("andúna"), emptyRepo))
        assertFalse(condition.matches(q.word("anca"), emptyRepo))
        assertEquals("second to last syllable contains 'ú'", condition.toEditableText())
    }

    @Test
    fun syllableMatcherDiphthong() {
        val condition =
            RuleCondition.parse(ParseBuffer("first syllable contains a diphthong"), q) as SyllableRuleCondition
        assertTrue(condition.matches(q.word("rauca"), emptyRepo))
        assertFalse(condition.matches(q.word("tie"), emptyRepo))
    }

    @Test
    fun syllableMatcherEndsWith() {
        val condition =
            RuleCondition.parse(ParseBuffer("first syllable ends with a consonant"), q) as SyllableRuleCondition
        assertTrue(condition.matches(q.word("ampa"), emptyRepo))
        assertFalse(condition.matches(q.word("tie"), emptyRepo))
    }

    @Test
    fun syllableCount() {
        val condition = RuleCondition.parse(ParseBuffer("number of syllables is 3"), q) as SyllableCountRuleCondition
        assertEquals(3, condition.expectCount)
        assertTrue(condition.matches(q.word("andúna"), emptyRepo))
        assertFalse(condition.matches(q.word("anca"), emptyRepo))
    }

    @Test
    fun syllableCountAtLeast() {
        val condition =
            RuleCondition.parse(ParseBuffer("number of syllables is at least 2"), q) as SyllableCountRuleCondition
        assertEquals(">=", condition.condition)
        assertEquals(2, condition.expectCount)
        assertTrue(condition.matches(q.word("andúna"), emptyRepo))
        assertTrue(condition.matches(q.word("anca"), emptyRepo))
        assertFalse(condition.matches(q.word("lo"), emptyRepo))
        assertEquals("number of syllables is at least 2", condition.toEditableText())
    }

    @Test
    fun syllableCountNegated() {
        val condition =
            RuleCondition.parse(ParseBuffer("number of syllables is not 3"), q) as SyllableCountRuleCondition
        assertEquals(3, condition.expectCount)
        assertFalse(condition.matches(q.word("andúna"), emptyRepo))
        assertTrue(condition.matches(q.word("anca"), emptyRepo))
    }

    @Test
    fun syllableCountInvalid() {
        assertThrows("Number of syllables should be a number", RuleParseException::class.java) {
            RuleCondition.parse(ParseBuffer("number of syllables is foo"), q) as LeafRuleCondition
        }
    }

    @Test
    fun stress() {
        val rule = parseRule(
            q, q, """
            number of syllables is 2:
            - stress is on first syllable
            """.trimIndent()
        )
        val word = rule.apply(q.word("lasse"), emptyRepo)
        assertEquals(1, word.stressedPhonemeIndex)
    }

    @Test
    fun serializeStressRule() {
        val rule = parseRule(
            q, q, """
            number of syllables is 2:
            - stress is on first syllable
            """.trimIndent()
        )

        val serializedRule = rule.ruleToSerializedFormat()
        val branches = ruleBranchesFromSerializedFormat(emptyRepo, q, q, serializedRule.branches)
        assertTrue(branches[0].instructions[0] is ApplyStressInstruction)
    }

    @Test
    fun preInstructions() {
        val ruleText = """
            | - prepend 'a'
            |word ends with 'r':
            | - append 'i'
        """.trimMargin("|")
        val rule = repo.rule(ruleText, q)
        assertEquals(1, rule.logic.preInstructions.size)

        val word = rule.apply(q.word("sur"), emptyRepo)
        assertEquals("asuri", word.text)

        assertEquals(ruleText, rule.toEditableText(repo))
    }

    @Test
    fun prepend() {
        val rule = parseRule(q, q, "- prepend first vowel")
        val instruction = rule.logic.branches[0].instructions[0]
        assertEquals("utul", instruction.apply(q.word("tul"), dummyContext).text)
        val data = instruction.toSerializedFormat()
        val deserialized = ruleInstructionFromSerializedFormat(emptyRepo, q, q, data)
        assertEquals("utul", deserialized.apply(q.word("tul"), dummyContext).text)
        assertEquals("prepend first vowel", instruction.toEditableText(repo))
    }

    @Test
    fun changeEndingToEmpty() {
        val rule = parseRule(q, q, "word ends with 'ea':\n- change ending to ''")
        val result = rule.apply(q.word("yaimea"), emptyRepo)
        assertEquals("yaim", result.text)
        assertEquals("change ending to ''", rule.singleInstruction().toEditableText(repo))
        assertEquals("", rule.logic.branches[0].toSummaryText(repo, false))
    }

    @Test
    fun changeEndingLongPhoneme() {
        val rule = parseRule(ce, ce, "word ends with consonant:\n- change ending to ''")
        val result = rule.apply(ce.word("mirth"), emptyRepo)
        assertEquals("mir", result.text)
    }

    @Test
    fun applyClass() {
        val rule = parseRule(q, q, "word ends with 'r':\n- mark word as strong")
        val result = rule.apply(q.word("anar"), emptyRepo)
        assertEquals("strong", result.classes.single())
        assertEquals("mark word as strong", rule.singleInstruction().toEditableText(repo))
    }

    private fun Rule.singleInstruction() = logic.branches.single().instructions.single()

    @Test
    fun stressCondition() {
        val ciryali = q.word("ciryali")
        ciryali.stressedPhonemeIndex = 1
        val condition = RuleCondition.parse(ParseBuffer("stress is on third to last syllable"), q)
        assertTrue(condition.matches(ciryali, emptyRepo))
        assertFalse(condition.matches(q.word("lasse").apply { stressedPhonemeIndex = 1 }, emptyRepo))
        assertEquals("stress is on third to last syllable", condition.toEditableText())
    }

    @Test
    fun insert() {
        val rule = parseRule(q, q, "- insert 'i' before last consonant")
        assertEquals("adain", rule.apply(q.word("adan"), emptyRepo).text)
        assertEquals("insert 'i' before last consonant", rule.singleInstruction().toEditableText(repo))
    }

    @Test
    fun absolutePhonemeRuleCondition() {
        val rule = parseRule(
            q, q, """
            last sound is consonant and second to last sound is vowel:
            - insert 'i' before last consonant
            otherwise:
            - no change
         """.trimIndent()
        )
        assertEquals("adain", rule.apply(q.word("adan"), emptyRepo).text)
        assertEquals("fela", rule.apply(q.word("fela"), emptyRepo).text)
        assertFalse(rule.logic.branches.first().condition.refersToPhoneme(q.phonemes.find { "a" in it.graphemes }!!))
    }

    @Test
    fun baseWord() {
        val rule = parseRule(
            q, q, """
            first sound of base word in CE is 'm':
            - prepend 'm'
            otherwise:
            - no change
         """.trimIndent()
        )

        val repo = repoWithQ().apply {
            addLanguage(ce)
        }
        val mbar = repo.addWord("mbar", language = ce)
        val bar = repo.addWord("bar")
        repo.addLink(bar, mbar, Link.Origin)

        assertEquals("mbar", rule.apply(bar, repo).text)
        assertEquals("first sound of base word in CE is 'm'", rule.logic.branches[0].condition.toEditableText())
    }

    @Test
    fun baseWordEndsWith() {
        val rule = parseRule(
            q, q, """
            base word in CE ends with 'm':
            - change ending to 'm'
         """.trimIndent()
        )

        val repo = repoWithQ().apply {
            addLanguage(ce)
        }
        val talam = repo.addWord("talam", language = ce)
        val talan = repo.addWord("talan")
        repo.addLink(talan, talam, Link.Origin)

        assertEquals("talam", rule.apply(talan, repo).text)
        assertEquals("base word in CE ends with 'm'", rule.logic.branches[0].condition.toEditableText())
    }

    @Test
    fun prependMorpheme() {
        val oe = Language("Old English", "OE")
        val repo = InMemoryGraphRepository().with(oe)
        val gePrefix = repo.addWord("ge-", "Prefix: ge", language = oe)
        val rule = repo.rule("- prepend morpheme 'ge-: Prefix: ge'", fromLanguage = oe)
        val frignan = repo.addWord("frignan", language = oe)
        val result = rule.apply(frignan, repo)
        assertEquals("gefrignan", result.text)

        assertEquals("prepend morpheme 'ge-: Prefix: ge'", rule.firstInstruction.toEditableText(repo))
        assertEquals("ge-", rule.firstInstruction.toSummaryText(repo, rule.logic.branches[0].condition))

        val instruction = rule.firstInstruction
        val data = instruction.toSerializedFormat()
        val deserialized = ruleInstructionFromSerializedFormat(repo, oe, oe, data)
        assertTrue(deserialized is MorphemeInstruction)
        assertEquals(gePrefix.id, (deserialized as MorphemeInstruction).morphemeId)
    }

    @Test
    fun appendMorpheme() {
        val on = Language("Old Norse", "ON")
        val repo = InMemoryGraphRepository().with(on)
        val inn = repo.addWord("inn", "the", language = on)
        val rule = repo.rule("- append morpheme 'inn: the'", fromLanguage = on)
        val hestr = repo.addWord("hestr", language = on)
        val result = rule.apply(hestr, repo)
        assertEquals("hestrinn", result.text)
        assertEquals(1, result.segments!!.size)
        assertEquals(inn, result.segments!![0].sourceWord)
        assertEquals(3, result.segments!![0].length)
    }

    @Test
    fun applyRuleUseExistingLink() {
        val on = Language("Old Norse", "ON")
        on.wordClasses = mutableListOf(WordCategory("Gender", listOf("N"), listOf(WordCategoryValue("Neuter", "n"))))
        val repo = InMemoryGraphRepository().with(on)
        val haust = repo.addWord("haust", "autumn", classes = listOf("n"), language = on)
        val accRule = repo.rule("- no change", fromLanguage = on, name = "on-acc", addedCategories = ".ACC")
        val haustAcc = repo.addWord("haust", "autumn.ACC", language = on)
        repo.addLink(haustAcc, haust, Link.Derived, listOf(accRule))
        val defRule = repo.rule("- apply rule 'on-acc'\nword is n:\n- append 'it'", fromLanguage = on)
        val result = defRule.apply(haust, repo)
        assertEquals("haustit", result.text)
    }

    @Test
    fun speRule() {
        val rule = parseRule(q, q, " * d -> l / #_")
        val result = rule.apply(q.word("danta"), repo)
        assertEquals("lanta", result.text)
        assertTrue(rule.refersToPhoneme(q.phonemes.find { it.graphemes[0] == "d" }!!))
    }

    @Test
    fun speRuleComment() {
        val ruleText = "$ SPE rule\n* d > l / #_"
        val rule = parseRule(q, q, ruleText)
        assertEquals(ruleText, rule.toEditableText(repo))
    }

    @Test
    fun speRuleInstructionComment() {
        val ruleText = "$ SPE rule\n* d > l / #_\n$ second instruction comment\n* a > i"
        val rule = parseRule(q, q, ruleText)
        assertEquals(ruleText, rule.toEditableText(repo))
    }

    @Test
    fun speRulePhonemic() {
        val rule = parseRule(q, q, " * z > r")
        val result = rule.apply(q.word("thuzya"), repo)
        assertEquals("thurja", result.text)
        assertTrue(result.isPhonemic)
    }

    @Test
    fun speRulePhonemicUnchanged() {
        val rule = parseRule(q, q, " * z -> r")
        val result = rule.apply(q.word("thurya").asPhonemic(), repo)
        assertEquals("thurja", result.text)
        assertTrue(result.isPhonemic)
    }


    @Test
    fun speRuleSegments() {
        val rule = parseRule(ce, q, """* ōj -> i""")
        val word = ce.word("grapōjan").apply {
            segments = listOf(WordSegment(4, 4, null, null, null, false))
            stressedPhonemeIndex = 6
            explicitStress = true
        }
        val newWord = rule.apply(word, emptyRepo)
        assertEquals(1, newWord.segments!!.size)
        assertEquals(3, newWord.segments!![0].length)
        assertEquals(5, newWord.stressedPhonemeIndex)
        assertTrue(newWord.explicitStress)
    }

    @Test
    fun speRuleWithCondition() {
        val text = "* a > i / _# if number of syllables is 1"
        val rule = parseRule(ce, q, text)
        val instruction = rule.singleInstruction() as SpeInstruction
        assertNotNull(instruction.condition)
        assertEquals("mi", rule.apply(q.word("ma"), repo).text)
        assertEquals("ama", rule.apply(q.word("ama"), repo).text)
        assertEquals(text, rule.toEditableText(repo))
        assertEquals(text.removePrefix("* "), instruction.toRichText(repo).toString())
    }

    @Test
    fun speApplySoundRule() {
        val speRule = repo.rule("* e > i", name = "q-e-i")
        val baseRule = repo.rule("word ends with 'a':\n- apply sound rule 'q-e-i' to second vowel\notherwise:\n- no change")
        assertEquals("elina", baseRule.apply(q.word("elena"), repo).text)
    }

    @Test
    fun speApplySoundRulePostInstruction() {
        val postRule = repo.rule("* e > i", name = "q-e-i")
        val baseRule = repo.rule("* a > 0\n= apply sound rule 'q-e-i' to previous vowel")
        assertEquals("elin", baseRule.apply(q.word("elena"), repo).text)
    }

    @Test
    fun notRule() {
        val text = "* a > i if not (previous sound is 'c')"
        val rule = parseRule(ce, q, text)
        assertEquals("mi", rule.apply(q.word("ma"), repo).text)
        assertEquals("ca", rule.apply(q.word("ca"), repo).text)
        assertEquals(text, rule.toEditableText(repo))
    }

    @Test
    fun remapStress() {
        val stressRule = parseRule(
            q, q, """
            - stress is on first syllable
            """.trimIndent()
        )
        ce.stressRule = RuleRef.to(stressRule)
        q.stressRule = RuleRef.to(stressRule)

        val word = ce.word("krop")
        assertEquals(2, word.calcStressedPhonemeIndex(repo))
        val rule = parseRule(q, q, "* kr > hr")
        val result = rule.apply(word, repo)
        assertEquals(1, result.stressedPhonemeIndex)
    }


    @Test
    fun remapStressSameLg() {
        val stressRule = parseRule(
            q, q, """
            - stress is on first syllable
            """.trimIndent()
        )
        q.stressRule = RuleRef.to(stressRule)

        val word = q.word("krop")
        assertEquals(2, word.calcStressedPhonemeIndex(repo))
        val rule = parseRule(q, q, "* kr > hr")
        val result = rule.apply(word, repo)
        assertEquals(1, result.stressedPhonemeIndex)
    }

    @Test
    fun remapStressMetathesis() {
        val stressRule = parseRule(
            q, q, """
            - stress is on first syllable
            """.trimIndent()
        )
        q.stressRule = RuleRef.to(stressRule)

        val word = q.word("krop")
        assertEquals(2, word.calcStressedPhonemeIndex(repo))
        val rule = parseRule(q, q, "* Vp > pV")
        val result = rule.apply(word, repo)
        assertEquals(3, result.stressedPhonemeIndex)
    }

    @Test
    fun applyRuleToSyllable() {
        val soundRule = parseRule(q, q, "* u > ú", "q-long")
        val repo = InMemoryGraphRepository()
        repo.addRule(soundRule)

        val text = "apply sound rule 'q-long' to first syllable"
        val instruction = RuleInstruction.parse("- $text", q.parseContext(repo))
        val context = RuleApplyContext(soundRule, null, emptyRepo)
        assertEquals("túl", instruction.apply(q.word("tul"), context).text)
        assertEquals(text, instruction.toEditableText(repo))
    }

    @Test
    fun applyRuleToStressedSyllable() {
        val soundRule = parseRule(q, q, "* u > ú", "q-long")
        val stressRule = parseRule(q, q, "- stress is on last syllable")
        q.stressRule = RuleRef.to(stressRule)
        val repo = InMemoryGraphRepository()
        repo.addRule(soundRule)

        val text = "apply sound rule 'q-long' to first stressed syllable"
        val instruction = RuleInstruction.parse("- $text", q.parseContext(repo))
        val context = RuleApplyContext(soundRule, null, repo)
        assertEquals("tulún", instruction.apply(q.word("tulun"), context).text)
        assertEquals(text, instruction.toEditableText(repo))
    }


    /*
    @Test
    fun chainedSummaryText() {
        val qNomPl = parseRule(q, q, """
            word ends with a vowel:
            - add suffix 'r'
            otherwise:
            - add suffix 'i'
        """.trimIndent(), "q-nom-pl")
        val repo = InMemoryGraphRepository()
        repo.addRule(qNomPl)

        val qGenPl = parseRule(q, q, """
            - apply rule 'q-nom-pl'
            - add suffix 'on'
            """.trimIndent(), repo = repo)
        assertEquals("-ron/-ion", qGenPl.toSummaryText())
    }
     */
}

fun Language.parseContext(repo: GraphRepository? = null, vararg rules: Rule): RuleParseContext =
    createParseContext(this, this, repo, *rules)

fun parseRule(
    fromLanguage: Language, toLanguage: Language, text: String, name: String = "q", repo: GraphRepository? = null,
    addedCategories: String? = null, fromPOS: List<String> = emptyList(), toPOS: String? = null,
    context: RuleParseContext? = null
): Rule = Rule(
    -1, name, fromLanguage, toLanguage,
    Rule.parseBranches(text, context ?: createParseContext(fromLanguage, toLanguage, repo)),
    addedCategories, null, fromPOS, toPOS, emptyList(), null
)

val Rule.firstInstruction get() = logic.branches[0].instructions[0]
