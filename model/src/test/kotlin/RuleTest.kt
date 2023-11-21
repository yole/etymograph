package ru.yole.etymograph

import org.junit.Assert.*
import org.junit.Test
import ru.yole.etymograph.JsonGraphRepository.Companion.ruleBranchesFromSerializedFormat
import ru.yole.etymograph.JsonGraphRepository.Companion.ruleInstructionFromSerializedFormat
import ru.yole.etymograph.JsonGraphRepository.Companion.ruleToSerializedFormat
import ru.yole.etymograph.JsonGraphRepository.Companion.toSerializedFormat

class RuleTest : QBaseTest() {
    private val dummyRule = parseRule(q, q, "- append 'a'")

    @Test
    fun conditions() {
        val c = LeafRuleCondition(ConditionType.EndsWith, v, null, false)
        assertTrue(c.matches(Word(0, "parma", q)))
        assertFalse(c.matches(Word(0, "formen", q)))
    }

    @Test
    fun instructions() {
        val i = PrependAppendInstruction(InstructionType.Append, q, "'a'")
        assertEquals("parma", i.apply(dummyRule, q.word("parm"), emptyRepo).text)
    }

    @Test
    fun rule() {
        val v = PhonemeClass("e", listOf("e", "ë"))
        val c = LeafRuleCondition(ConditionType.EndsWith, v, null, false)
        val i2 = PrependAppendInstruction(InstructionType.Append, q, "'i'")
        val r = RuleBranch(c, listOf(i2))

        assertTrue(r.matches(Word(0, "lasse", q)))
        assertEquals("atani", r.apply(dummyRule, q.word("atan"), emptyRepo).text)
    }

    @Test
    fun conditionParse() {
        val c = LeafRuleCondition.parse("word ends with 'eë'", q) as LeafRuleCondition
        assertEquals(ConditionType.EndsWith, c.type)
        assertEquals("eë", c.parameter)

        val c2 = LeafRuleCondition.parse("word ends with a vowel", q) as LeafRuleCondition
        assertEquals(ConditionType.EndsWith, c2.type)
        assertEquals(v, c2.phonemeClass)
    }

    @Test
    fun conditionParseOr() {
        val c = RuleCondition.parse("word ends with 'e' or word ends with 'ë'", q)
        assertTrue(c is OrRuleCondition)
        val l1 = (c as OrRuleCondition).members[0] as LeafRuleCondition
        assertEquals(ConditionType.EndsWith, l1.type)
        assertEquals("e", l1.parameter)
    }

    @Test
    fun conditionParseAnd() {
        val c = RuleCondition.parse("word ends with a vowel and word ends with 'a'", q)
        assertTrue(c is AndRuleCondition)
        val l1 = (c as AndRuleCondition).members[0] as LeafRuleCondition
        assertEquals(ConditionType.EndsWith, l1.type)
        assertEquals("vowel", l1.phonemeClass?.name)
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
        val b = RuleBranch.parse("""
            word ends with 'e':
            - append 'a'
        """.trimIndent(), q.parseContext())
        assertEquals("e", (b.condition as LeafRuleCondition).parameter)
        assertEquals("'a'", b.instructions[0].arg)
    }

    @Test
    fun ruleParse() {
        val branches = Rule.parseBranches("""
            word ends with 'e':
            - append 'a'
            word ends with 'i':
            - append 'r'
        """.trimIndent(), q.parseContext()).branches
        assertEquals(2, branches.size)
        assertEquals(1, branches[0].instructions.size)
        assertEquals(1, branches[1].instructions.size)
    }

    @Test
    fun ruleParseWithoutConditions() {
        val branches = Rule.parseBranches("""
            - append 'lye'
        """.trimIndent(), q.parseContext()).branches
        assertEquals(1, branches.size)
        assertEquals(1, branches[0].instructions.size)
        assertTrue(branches[0].matches(Word(0, "abc", q)))
    }

    @Test
    fun ruleParseOtherwise() {
        val branches = Rule.parseBranches("""
            word ends with 'e':
            - append 'a'
            otherwise:
            - append 'r'
        """.trimIndent(), q.parseContext()).branches
        assertEquals(2, branches.size)
        assertEquals(1, branches[0].instructions.size)
        assertEquals(OtherwiseCondition, branches[1].condition)
    }

    @Test
    fun phonemeIterator() {
        val it = PhonemeIterator(ce.word("khith"))
        assertEquals("kh", it.current)
        assertTrue(it.advance())
        assertEquals("i", it.current)
        assertTrue(it.advance())
        assertEquals("th", it.current)
        assertFalse(it.advance())
    }

    @Test
    fun phonemeCondition() {
        val it = PhonemeIterator(ce.word("khith"))
        val cond = LeafRuleCondition(ConditionType.PhonemeMatches, null, "kh", false)
        assertTrue(cond.matches(it))
    }

    @Test
    fun phonemeConditionParse() {
        val it = PhonemeIterator(ce.word("khith"))
        val cond = RuleCondition.parse("sound is 'kh'", ce)
        assertTrue(cond.matches(it))
    }

    @Test
    fun soundCorrespondence() {
        val rule = parseRule(
            ce, q,
            """
            sound is 'th':
            - new sound is 's'
            sound is 'kh':
            - new sound is 'h'
            """.trimIndent()
        )
        assertEquals("his", rule.apply(ce.word("khith"), emptyRepo).text)
    }

    @Test
    fun soundDisappears() {
        val rule = parseRule(ce, q, """
            sound is 'i':
            - sound disappears
            sound is 'th':
            - new sound is 's'
        """.trimIndent())
        assertEquals("khs", rule.apply(ce.word("khithi"), emptyRepo).text)
    }

    @Test
    fun previousSound() {
        val rule = parseRule(ce, q, """
            sound is 'i' and previous sound is 'kh':
            - sound disappears
        """.trimIndent())
        assertEquals("khthi", rule.apply(ce.word("khithi"), emptyRepo).text)
    }

    @Test
    fun previousSoundNegated() {
        val rule = parseRule(ce, q, """
            sound is 'i' and previous sound is not 'kh':
            - sound disappears
        """.trimIndent())
        assertEquals("khith", rule.apply(ce.word("khithi"), emptyRepo).text)
    }

    @Test
    fun applySoundRule() {
        val soundRule = parseRule(q, q, """
            sound is 'a':
            - new sound is 'á'
        """.trimIndent())
        val applySoundRuleInstruction = ApplySoundRuleInstruction(q, RuleRef.to(soundRule), "first vowel")
        assertEquals("lásse", applySoundRuleInstruction.apply(soundRule, q.word("lasse"), emptyRepo).text)
    }

    @Test
    fun parseApplySoundRule() {
        val soundRule = parseRule(q, q, """
            sound is 'a':
            - new sound is 'á'
        """.trimIndent())
        val parseContext = RuleParseContext(q, q) {
            if (it == "q-lengthen") RuleRef.to(soundRule) else throw RuleParseException("no such rule")
        }
        val applySoundRule = Rule(-1, "q-lengthen", q, q, Rule.parseBranches("""
            - apply sound rule 'q-lengthen' to first vowel
        """.trimIndent(), parseContext), null, null, null, null, emptyList(), null)
        assertEquals("lásse", applySoundRule.apply(q.word("lasse"), emptyRepo).text)
    }

    @Test
    fun soundRuleToEditableText() {
        val soundRule = parseRule(q, q, """
            sound is 'a':
            - new sound is 'á'
        """.trimIndent())
        val applySoundRuleInstruction = ApplySoundRuleInstruction(q, RuleRef.to(soundRule), "first vowel")
        assertEquals("apply sound rule 'q' to first vowel", applySoundRuleInstruction.toEditableText())
    }

    @Test
    fun beginningOfWord() {
        val rule = parseRule(ce, q, """
            beginning of word and sound is 'd':
            - new sound is 'l'
        """.trimIndent())
        assertEquals("lanta", rule.apply(ce.word("danta"), emptyRepo).text)
        assertEquals("beginning of word and sound is 'd'", rule.logic.branches[0].condition.toEditableText())
    }

    @Test
    fun syllableMatcher() {
        val condition = RuleCondition.parse("second to last syllable contains a long vowel", q) as SyllableRuleCondition
        assertEquals(-2, condition.index)
        assertEquals("long vowel", condition.phonemeClass.name)
        assertTrue(condition.matches(q.word("andúna")))
        assertFalse(condition.matches(q.word("anca")))
        assertEquals("second to last syllable contains a long vowel", condition.toEditableText())
    }

    @Test
    fun syllableMatcherDiphthong() {
        val condition = RuleCondition.parse("first syllable contains a diphthong", q) as SyllableRuleCondition
        assertTrue(condition.matches(q.word("rauca")))
        assertFalse(condition.matches(q.word("tie")))
    }

    @Test
    fun syllableMatcherEndsWith() {
        val condition = RuleCondition.parse("first syllable ends with a consonant", q) as SyllableRuleCondition
        assertTrue(condition.matches(q.word("ampa")))
        assertFalse(condition.matches(q.word("tie")))
    }

    @Test
    fun syllableCount() {
        val condition = RuleCondition.parse("number of syllables is 3", q) as LeafRuleCondition
        assertEquals("3", condition.parameter)
        assertTrue(condition.matches(q.word("andúna")))
        assertFalse(condition.matches(q.word("anca")))
    }

    @Test
    fun stress() {
        val rule = parseRule(q, q, """
            number of syllables is 2:
            - stress is on first syllable
            """.trimIndent()
        )
        val word = rule.apply(q.word("lasse"), emptyRepo)
        assertEquals(1, word.stressedPhonemeIndex)
    }

    @Test
    fun serializeStressRule() {
        val rule = parseRule(q, q, """
            number of syllables is 2:
            - stress is on first syllable
            """.trimIndent()
        )

        val serializedRule = rule.ruleToSerializedFormat()
        val branches = ruleBranchesFromSerializedFormat(emptyRepo, q, serializedRule.branches)
        assertTrue(branches[0].instructions[0] is ApplyStressInstruction)
    }

    @Test
    fun preInstructions() {
        val ruleText = """
            | - prepend 'a'
            |word ends with 'r':
            | - append 'i'
        """.trimMargin("|")
        val rule = parseRule(q, q, ruleText)
        assertEquals(1, rule.logic.preInstructions.size)

        val word = rule.apply(q.word("sur"), emptyRepo)
        assertEquals("asuri", word.text)

        assertEquals(ruleText, rule.toEditableText())
    }

    @Test
    fun prepend() {
        val rule = parseRule(q, q, "- prepend first vowel")
        val instruction = rule.logic.branches[0].instructions[0]
        assertEquals("utul", instruction.apply(rule, q.word("tul"), emptyRepo).text)
        val data = instruction.toSerializedFormat()
        val deserialized = ruleInstructionFromSerializedFormat(emptyRepo, q, data)
        assertEquals("utul", deserialized.apply(rule, q.word("tul"), emptyRepo).text)
        assertEquals("prepend first vowel", instruction.toEditableText())
    }

    @Test
    fun changeEndingToEmpty() {
        val rule = parseRule(q, q, "word ends with 'ea':\n- change ending to ''")
        val result = rule.apply(q.word("yaimea"), emptyRepo)
        assertEquals("yaim", result.text)
        assertEquals("change ending to ''", rule.logic.branches.single().instructions.single().toEditableText())
    }

    @Test
    fun stressCondition() {
        val ciryali = q.word("ciryali")
        ciryali.stressedPhonemeIndex = 2
        val condition = RuleCondition.parse("stress is on third to last syllable", q)
        assertTrue(condition.matches(ciryali))
        assertFalse(condition.matches(q.word("lasse").apply { stressedPhonemeIndex = 1 }))
        assertEquals("stress is on third to last syllable", condition.toEditableText())
    }

    @Test
    fun reverseApply() {
        val rule = parseRule(q, q, "- append 'llo'")
        val candidates = rule.reverseApply(q.word("hrestallo"))
        assertEquals(1, candidates.size)
        assertEquals("hresta", candidates[0])
    }

    @Test
    fun reverseApplyAppend() {
        val rule = parseRule(q, q, "- append 'llo'")
        val candidates = rule.reverseApply(q.word("hrestallo"))
        assertEquals(1, candidates.size)
        assertEquals("hresta", candidates[0])
    }

    @Test
    fun reverseApplyNormalize() {
        val rule = parseRule(q, q, "- append 'sse'")
        val candidates = rule.reverseApply(q.word("auressë"))
        assertEquals(1, candidates.size)
        assertEquals("aure", candidates[0])
    }

    @Test
    fun reverseApplyMatch() {
        val rule = parseRule(q, q, "word ends with a consonant:\n- append 'i'")
        val candidate = rule.reverseApply(q.word("nai"))
        assertEquals(0, candidate.size)
    }

    @Test
    fun reverseApplyChangeEnding() {
        val rule = parseRule(q, q, "word ends with 'ea':\n- change ending to 'ie'")
        val candidate = rule.reverseApply(q.word("yaimie"))
        assertEquals("yaimea", candidate.single())
    }

    @Test
    fun reverseApplyChangeEndingOr() {
        val rule = parseRule(q, q, "word ends with 'ea' or word ends with 'ao':\n- change ending to 'ie'")
        val candidates = rule.reverseApply(q.word("yaimie"))
        assertEquals(2, candidates.size)
        assertTrue("yaimao" in candidates)
    }

    @Test
    fun reverseApplyToPhoneme() {
        val rule = parseRule(q, q, "sound is 'i':\n- new sound is 'í'")
        val phonemes = PhonemeIterator(q.word("círa"))
        phonemes.advanceTo(1)
        assertTrue(rule.reverseApplyToPhoneme(phonemes))
        assertEquals("cira", phonemes.result())

        val applySoundRuleInstruction = ApplySoundRuleInstruction(q, RuleRef.to(rule), "first vowel")
        assertEquals("cira", applySoundRuleInstruction.reverseApply(rule, "círa", q).single())
    }

    @Test
    fun reverseApplyMultiple() {
        val rule = parseRule(q, q, "word ends with a consonant:\n- append 'ala'\notherwise:\n- append 'la'")
        val candidates = rule.reverseApply(q.word("picala"))
        assertEquals(2, candidates.size)
    }

    @Test
    fun segment() {
        val rule = parseRule(q, q, "- append 'llo'")
        val result = rule.apply(q.word("hresta"), emptyRepo)
        assertEquals(1, result.segments!!.size)
        val segment = result.segments!![0]
        assertEquals(6, segment.firstCharacter)
        assertEquals(3, segment.length)
        assertEquals(rule, segment.sourceRule)
    }

    @Test
    fun segmentAppend() {
        val rule = parseRule(q, q, "- append 'llo'")
        val result = rule.apply(q.word("hresta"), emptyRepo)
        assertEquals(1, result.segments!!.size)
        val segment = result.segments!![0]
        assertEquals(6, segment.firstCharacter)
        assertEquals(3, segment.length)
        assertEquals(rule, segment.sourceRule)
    }

    @Test
    fun multipleSegments() {
        val rule1 = parseRule(q, q, "- append 'llo'")
        val rule2 = parseRule(q, q, "- append 's'")
        val result = rule2.apply(rule1.apply(q.word("hresta"), emptyRepo), emptyRepo)
        assertEquals(2, result.segments!!.size)
        /*
        val segment = result.segments!![0]
        assertEquals(6, segment.firstCharacter)
        assertEquals(3, segment.length)
        assertEquals(rule, segment.sourceRule)
         */
    }

    @Test
    fun restoreSegments() {
        val repo = InMemoryGraphRepository()
        val hresta = repo.addWord("hresta")
        val hrestallo = repo.addWord("hrestallo", gloss = null)
        val rule = parseRule(q, q, "- append 'llo'", addedCategories = ".ABL")
        val link = repo.addLink(hrestallo, hresta, Link.Derived, listOf(rule), emptyList(), null)
        val restored = repo.restoreSegments(hrestallo)
        assertEquals(1, restored.segments!!.size)
        assertEquals("hresta-llo", restored.segmentedText())
        assertEquals("hresta-ABL", restored.getOrComputeGloss(repo))
    }

    @Test
    fun restoreSegmentsNoChange() {
        val repo = InMemoryGraphRepository()
        val hresta2 = repo.addWord("hresta", gloss = null)
        val hresta = repo.addWord("hresta", gloss = "hresta")
        val rule = parseRule(q, q, "word ends with 'i':\n- no change", addedCategories = ".ABL")
        val link = repo.addLink(hresta2, hresta, Link.Derived, listOf(rule), emptyList(), null)
        val restored = repo.restoreSegments(hresta2)
        assertEquals("hresta.ABL", restored.getOrComputeGloss(repo))
    }

    @Test
    fun chainedSegments() {
        val qNomPl = parseRule(q, q, """
            word ends with a vowel:
            - append 'r'
            otherwise:
            - append 'i'
        """.trimIndent(), "q-nom-pl")
        val repo = InMemoryGraphRepository()
        repo.addRule(qNomPl)

        val qGenPl = parseRule(q, q, """
            - apply rule 'q-nom-pl'
            - append 'on'
            """.trimIndent(), repo = repo)
        val result = qGenPl.apply(q.word("alda"), repo)
        assertEquals(1, result.segments!!.size)
        val segment = result.segments!![0]
        assertEquals(4, segment.firstCharacter)
        assertEquals(3, segment.length)
    }

    @Test
    fun testChainTwoSegments() {
        val repo = InMemoryGraphRepository()
        val qPpl = parseRule(q, q, "- append 'li'", "q-ppl")
        repo.addRule(qPpl)
        val qAll = parseRule(q, q, "- append 'nna'", "q-all")
        repo.addRule(qAll)

        val qAllPpl = parseRule(q, q, """
            - apply rule 'q-ppl'
            - apply rule 'q-all'
            - append 'r'
        """.trimIndent(), repo = repo)
        val result = qAllPpl.apply(q.word("falma"), repo)
        assertEquals(1, result.segments!!.size)
    }

    /*

    @Test
    fun applyRuleToSyllable() {
        val soundRule = parseRule(q, q, """
            sound is 'u':
            - new sound is 'ú'
        """.trimIndent(), "q-long")
        val repo = InMemoryGraphRepository()
        repo.addRule(soundRule)

        val instruction = RuleInstruction.parse("- apply sound rule 'q-long' to first syllable", q.parseContext(repo))
        assertEquals("túl", instruction.apply(q.word("tul"), emptyRepo).text)
    }
     */

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

fun Language.word(text: String, gloss: String? = null) = Word(-1, text, this, gloss)

fun Language.parseContext(repo: GraphRepository? = null) = createParseContext(this, this, repo)

fun parseRule(
    fromLanguage: Language, toLanguage: Language, text: String, name: String = "q", repo: GraphRepository? = null,
    addedCategories: String? = null, fromPOS: String? = null, toPOS: String? = null
): Rule = Rule(
    -1, name, fromLanguage, toLanguage,
    Rule.parseBranches(text, createParseContext(fromLanguage, toLanguage, repo)),
    addedCategories, null, fromPOS, toPOS, emptyList(), null
)

private fun createParseContext(
    fromLanguage: Language,
    toLanguage: Language,
    repo: GraphRepository?
) = RuleParseContext(fromLanguage, toLanguage) {
    repo?.ruleByName(it)?.let { RuleRef.to(it) } ?: throw RuleParseException("no such rule")
}
