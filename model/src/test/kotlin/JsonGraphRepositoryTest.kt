package ru.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.yole.etymograph.JsonGraphRepository.Companion.ruleBranchesFromSerializedFormat
import ru.yole.etymograph.JsonGraphRepository.Companion.ruleToSerializedFormat

class JsonGraphRepositoryTest : QBaseTest() {
    lateinit var repo: JsonGraphRepository

    @Before
    fun setup() {
        repo = JsonGraphRepository(null)
        repo.addLanguage(q)
    }

    @Test
    fun deletedWords() {
        val abc = repo.addWord("abc")
        val def = repo.addWord("def")
        repo.deleteWord(abc)

        val repo2 = repo.roundtrip()
        assertEquals(null, repo2.wordById(abc.id))
        assertEquals("def", repo2.wordById(def.id)!!.text)
    }

    @Test
    fun serializeApplySoundRule() {
        val soundRule = repo.addRule(
            "q-lengthen", q, q,
            Rule.parseBranches("* a > á", q.parseContext()))

        val parseContext = RuleParseContext(repo, q, q) {
            if (it == "q-lengthen") RuleRef.to(soundRule) else throw RuleParseException("no such rule")
        }
        val applySoundRule = Rule(-1, "lengthen-first-vowel", q, q, Rule.parseBranches("""
            - apply sound rule 'q-lengthen' to first vowel
        """.trimIndent(), parseContext))

        val serializedData = applySoundRule.ruleToSerializedFormat()
        assertEquals(2, serializedData.branches[0].instructions[0].args.size)

        val branches = ruleBranchesFromSerializedFormat(repo, q, q, serializedData.branches)
        val insn = branches[0].instructions[0] as ApplySoundRuleInstruction
        assertEquals("q-lengthen", insn.ruleRef.resolve().name)
        assertEquals("vowel", insn.seekTarget!!.phonemeClass!!.name)
    }

    @Test
    fun serializeRelativePhonemeRule() {
        val rule = parseRule(q, q, """
            sound is 'i' and previous sound is not vowel:
            - sound disappears
        """.trimIndent())

        val serializedData = rule.ruleToSerializedFormat()
        val branches = ruleBranchesFromSerializedFormat(repo, q, q, serializedData.branches)
        assertEquals("sound is 'i' and previous sound is not vowel", branches[0].condition.toEditableText())
    }

    @Test
    fun serializePhonemeEqualsCondition() {
        val text = "C > 0 if sound is same as previous sound"
        val rule = parseRule(q, q, "* $text")

        val serializedData = rule.ruleToSerializedFormat()
        val branches = ruleBranchesFromSerializedFormat(repo, q, q, serializedData.branches)
        assertEquals(text, branches[0].instructions[0].toEditableText(repo))
    }

    @Test
    fun serializeSpeRule() {
        val rule = parseRule(q, q, "* d > l / #_ if number of syllables is 1")

        val serializedData = rule.ruleToSerializedFormat()
        val branches = ruleBranchesFromSerializedFormat(repo, q, q, serializedData.branches)
        val insn = branches[0].instructions[0]
        assertTrue(insn is SpeInstruction)
        assertEquals("d > l / #_ if number of syllables is 1", insn.toEditableText(repo))
    }

    @Test
    fun serializeInsertInstruction() {
        val rule = parseRule(q, q, """
            - insert 'i' before last consonant
        """.trimIndent())

        val serializedData = rule.ruleToSerializedFormat()
        val branches = ruleBranchesFromSerializedFormat(repo, q, q, serializedData.branches)
        assertEquals("insert 'i' before last consonant", branches[0].instructions[0].toEditableText(repo))
    }

    @Test
    fun serializeBranchComment() {
        val rule = parseRule(q, q, """
            $ This is a comment
            - insert 'i' before last consonant
        """.trimIndent())

        val serializedData = rule.ruleToSerializedFormat()
        val branches = ruleBranchesFromSerializedFormat(repo, q, q, serializedData.branches)
        assertEquals("This is a comment", branches[0].comment)
    }

    @Test
    fun serializeInstructionComment() {
        val rule = parseRule(q, q, """
            $ This is a comment
            - insert 'i' before last consonant
            $ This is an instruction comment
            - insert 'i' before last consonant
        """.trimIndent())

        val serializedData = rule.ruleToSerializedFormat()
        val branches = ruleBranchesFromSerializedFormat(repo, q, q, serializedData.branches)
        assertEquals("This is an instruction comment", branches[0].instructions[1].comment)
    }

    @Test
    fun serializePostInstructions() {
        val rule = parseRule(q, q, """
            word ends with 'i':
            - append 'r'
            = append 'e'
        """.trimIndent())
        val serializedData = rule.ruleToSerializedFormat()
        val rule2 = repo.ruleFromSerializedFormat(serializedData, q, q)
        assertEquals(1, rule2.logic.postInstructions.size)
    }

    @Test
    fun serializeNot() {
        val text = "* a > i if not (previous sound is 'c')"
        val rule = parseRule(q, q, text)
        val serializedData = rule.ruleToSerializedFormat()
        val rule2 = repo.ruleFromSerializedFormat(serializedData, q, q)
        assertEquals(text, rule2.toEditableText(repo))
    }

    @Test
    fun serializeTranslation() {
        val corpusText = repo.addCorpusText("abc", null, q)
        repo.addTranslation(corpusText, "def", emptyList())
        val repo2 = repo.roundtrip()
        val corpusText2 = repo2.corpusTextById(corpusText.id)!!
        assertEquals(1, repo2.translationsForText(corpusText2).size)
    }

    private fun JsonGraphRepository.roundtrip(): JsonGraphRepository {
        val jsonFiles = mutableMapOf<String, String>()
        saveToJson { path, content -> jsonFiles[path] = content }
        return JsonGraphRepository.fromJsonProvider { jsonFiles[it] }
    }

    @Test
    fun serializeDeleteParadigm() {
        val np = repo.addParadigm("Noun", q, listOf("N"))
        val vp = repo.addParadigm("Verb", q, listOf("V"))
        repo.deleteParadigm(np)
        assertEquals(1, repo.allParadigms().size)
        val repo2 = repo.roundtrip()
        assertEquals(1, repo2.allParadigms().size)
        assertEquals("Verb", repo2.paradigmById(vp.id)!!.name)
    }

    @Test
    fun serializePhonemes() {
        q.phonemes = mutableListOf(phoneme("a", "front open vowel"))
        val repo2 = repo.roundtrip()
        assertEquals(1, repo2.languageByShortName("Q")!!.phonemes.size)
    }

    @Test
    fun serializeRuleSequence() {
        setupRuleSequence()
        val repo2 = repo.roundtrip()
        val sequences = repo2.ruleSequencesForLanguage(repo2.languageByShortName("Q")!!)
        assertEquals(1, sequences.size)
        assertEquals("i-disappears", sequences[0].resolveRules(repo2).single().name)
    }

    private fun setupRuleSequence(): RuleSequence {
        repo.addLanguage(ce)
        val rule = repo.addRule(
            "i-disappears", ce, q,
            Rule.parseBranches(
                """
                sound is 'i' and previous sound is 'a':
                - sound disappears
            """.trimIndent(), q.parseContext(repo)
            )
        )
        return repo.addRuleSequence("ce-to-q", ce, q, listOf(RuleSequenceStep(rule, null,false, false)))
    }

    @Test
    fun serializeOrthographyRule() {
        val rule = repo.addRule("q-ortho", q, q,
            Rule.parseBranches("sound is word-initial 'j':\n - new sound is 'y'", q.parseContext(repo)))
        q.orthographyRule = RuleRef.to(rule)

        val repo2 = repo.roundtrip()
        assertEquals("q-ortho", repo2.languageByShortName("Q")!!.orthographyRule!!.resolve().name)
    }

    @Test
    fun serializeExplicitStress() {
        val word = repo.findOrAddWord("ea", q, null)
        word.stressedPhonemeIndex = 1
        word.explicitStress = true
        val repo2 = repo.roundtrip()
        val word2 = repo2.wordsByText(repo2.languageByShortName("Q")!!, "ea").single()
        assertEquals(1, word2.stressedPhonemeIndex)
        assertEquals(true, word2.explicitStress)
    }

    @Test
    fun serializeCompound() {
        val baseWord = repo.addWord("mann")
        val prefix = repo.addWord("sæ")
        val compoundWord = repo.addWord("sæmann")
        repo.createCompound(compoundWord, listOf(prefix, baseWord), headIndex = 1)
        val repo2 = repo.roundtrip()
        val compoundWord2 = repo2.wordsByText(repo2.languageByShortName("Q")!!, "sæmann").single()
        val compound2 = repo2.findCompoundsByCompoundWord(compoundWord2).single()
        assertEquals(1, compound2.headIndex)
    }

    @Test
    fun serializeParadigmRules() {
        val paradigm = repo.addParadigm("Noun", q, listOf("N"))
        val preRule = repo.rule("word ends with 'a':\n- change ending to ''", name = "q-pre")
        val postRule = repo.rule("word ends with 'oo':\n - change ending to 'o'", name = "q-post")
        paradigm.preRule = preRule
        paradigm.postRule = postRule
        val repo2 = repo.roundtrip()
        assertEquals(1, repo2.paradigmsForLanguage(repo2.languageByShortName("Q")!!).size)
        assertEquals("q-pre", repo2.paradigmById(paradigm.id)!!.preRule!!.name)
        assertEquals("q-post", repo2.paradigmById(paradigm.id)!!.postRule!!.name)
    }

    @Test
    fun serializeLinkRuleSequence() {
        val seq = setupRuleSequence()
        val lai = repo.addWord("lai", language = ce)
        val la = repo.addWord("la", language = q)
        val link = repo.addLink(la, lai, Link.Origin)
        repo.applyRuleSequence(link, seq)
        val repo2 = repo.roundtrip()
        val la2 = repo2.wordById(la.id)!!
        val link2 = repo2.getLinksFrom(la2).single()
        assertEquals(seq.name, link2.sequence!!.name)
    }

    @Test
    fun serializeProtoLanguage() {
        repo.addLanguage(ce)
        val qLang = repo.languageByShortName("Q")!!
        qLang.protoLanguage = ce

        val repo2 = repo.roundtrip()
        val q2 = repo2.languageByShortName("Q")!!
        assertEquals("CE", q2.protoLanguage?.shortName)
    }
}
