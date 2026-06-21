package ru.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.yole.etymograph.JsonGraph.Companion.ruleBranchesFromSerializedFormat
import ru.yole.etymograph.JsonGraph.Companion.ruleToSerializedFormat

class JsonGraphTest {
    lateinit var graph: JsonGraph
    lateinit var q: Language
    lateinit var ce: Language

    @Before
    fun setup() {
        graph = JsonGraph(null)
        q = quenya(graph)
        ce = graph.addLanguage("Common Eldarin", "CE")
    }

    @Test
    fun deletedWords() {
        val abc = graph.addWord("abc", q, null)
        val def = graph.addWord("def", q, null)
        graph.deleteWord(abc)

        val repo2 = graph.roundtrip()
        assertEquals(null, repo2.wordById(abc.id))
        assertEquals("def", repo2.wordById(def.id)!!.text)
    }

    @Test
    fun serializeApplySoundRule() {
        val soundRule = graph.addRule(
            "q-lengthen", q, q,
            Rule.parseLogic("* a > á", q.parseContext()))

        val parseContext = RuleParseContext(q, q) {
            if (it == "q-lengthen") RuleRef.to(soundRule) else throw RuleParseException("no such rule")
        }
        val applySoundRule = Rule(-1, "lengthen-first-vowel", q, q, Rule.parseLogic("""
            - apply sound rule 'q-lengthen' to first vowel
        """.trimIndent(), parseContext))

        val serializedData = applySoundRule.ruleToSerializedFormat()
        assertEquals(2, serializedData.branches[0].instructions[0].args.size)

        val branches = ruleBranchesFromSerializedFormat(graph, q, q, serializedData.branches)
        val insn = branches[0].instructions[0] as ApplySoundRuleInstruction
        assertEquals("q-lengthen", insn.ruleRef.resolve().name)
        assertEquals("vowel", insn.seekTarget!!.phonemeClass!!.name)
    }

    @Test
    fun serializeRelativePhonemeRule() {
        val text = "i > 0 if previous sound is not vowel"
        val rule = parseRule(q, q, "* $text")

        val serializedData = rule.ruleToSerializedFormat()
        val branches = ruleBranchesFromSerializedFormat(graph, q, q, serializedData.branches)
        assertEquals(text, branches[0].instructions[0].toEditableText(graph))
    }

    @Test
    fun serializePhonemeEqualsCondition() {
        val text = "C > 0 if sound is same as previous sound"
        val rule = parseRule(q, q, "* $text")

        val serializedData = rule.ruleToSerializedFormat()
        val branches = ruleBranchesFromSerializedFormat(graph, q, q, serializedData.branches)
        assertEquals(text, branches[0].instructions[0].toEditableText(graph))
    }

    @Test
    fun serializeSpeRule() {
        val rule = parseRule(q, q, "* d > l / #_ if number of syllables is 1")

        val serializedData = rule.ruleToSerializedFormat()
        val branches = ruleBranchesFromSerializedFormat(graph, q, q, serializedData.branches)
        val insn = branches[0].instructions[0]
        assertTrue(insn is SpeInstruction)
        assertEquals("d > l / #_ if number of syllables is 1", insn.toEditableText(graph))
    }

    @Test
    fun serializeInsertInstruction() {
        val rule = parseRule(q, q, """
            - insert 'i' before last consonant
        """.trimIndent())

        val serializedData = rule.ruleToSerializedFormat()
        val branches = ruleBranchesFromSerializedFormat(graph, q, q, serializedData.branches)
        assertEquals("insert 'i' before last consonant", branches[0].instructions[0].toEditableText(graph))
    }

    @Test
    fun serializeBranchComment() {
        val rule = parseRule(q, q, """
            $ This is a comment
            - insert 'i' before last consonant
        """.trimIndent())

        val serializedData = rule.ruleToSerializedFormat()
        val branches = ruleBranchesFromSerializedFormat(graph, q, q, serializedData.branches)
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
        val branches = ruleBranchesFromSerializedFormat(graph, q, q, serializedData.branches)
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
        val rule2 = graph.ruleFromSerializedFormat(serializedData, q, q)
        assertEquals(1, (rule2.logic as MorphoRuleLogic).postInstructions.size)
    }

    @Test
    fun serializeNot() {
        val text = "* a > i if not (previous sound is 'c')"
        val rule = parseRule(q, q, text)
        val serializedData = rule.ruleToSerializedFormat()
        val rule2 = graph.ruleFromSerializedFormat(serializedData, q, q)
        assertEquals(text, rule2.toEditableText(graph))
    }

    @Test
    fun serializeTranslation() {
        val corpusText = graph.addCorpusText("abc", null, q)
        val translation = graph.addTranslation(corpusText, "def", emptyList())
        translation.anchorStartIndex = 0
        translation.anchorEndIndex = 1
        val repo2 = graph.roundtrip()
        val corpusText2 = repo2.corpusTextById(corpusText.id)!!
        val translation2 = repo2.translationsForText(corpusText2).single()
        assertEquals(0, translation2.anchorStartIndex)
        assertEquals(1, translation2.anchorEndIndex)
    }

    private fun JsonGraph.roundtrip(): JsonGraph {
        val jsonFiles = mutableMapOf<String, String>()
        saveToJson { path, content -> jsonFiles[path] = content }
        return JsonGraph.fromJsonProvider { jsonFiles[it] }
    }

    @Test
    fun serializeDeleteParadigm() {
        val np = graph.addParadigm("Noun", q, listOf("N"))
        val vp = graph.addParadigm("Verb", q, listOf("V"))
        graph.deleteParadigm(np)
        assertEquals(1, graph.allParadigms().size)
        val repo2 = graph.roundtrip()
        assertEquals(1, repo2.allParadigms().size)
        assertEquals("Verb", repo2.paradigmById(vp.id)!!.name)
    }

    @Test
    fun serializePhonemes() {
        q.phonemes = mutableListOf(phoneme("a", "front open vowel"))
        val repo2 = graph.roundtrip()
        assertEquals(1, repo2.languageByShortName("Q")!!.phonemes.size)
    }

    @Test
    fun serializeRuleSequence() {
        setupRuleSequence()
        val repo2 = graph.roundtrip()
        val sequences = repo2.ruleSequencesForLanguage(repo2.languageByShortName("Q")!!)
        assertEquals(1, sequences.size)
        assertEquals("i-disappears", sequences[0].resolveRules().single().name)
    }

    private fun setupRuleSequence(): RuleSequence {
        val rule = graph.addRule(
            "i-disappears", ce, q,
            Rule.parseLogic("* i > 0 / a_ ", q.parseContext(graph))
        )
        return graph.addRuleSequence("ce-to-q", ce, q, listOf(RuleSequenceStep(rule, null,false, false)))
    }

    @Test
    fun serializeOrthographyRule() {
        val rule = graph.addRule("q-ortho", q, q,
            Rule.parseLogic("* u > j / #_", q.parseContext(graph)))
        q.orthographyRule = RuleRef.to(rule)

        val repo2 = graph.roundtrip()
        assertEquals("q-ortho", repo2.languageByShortName("Q")!!.orthographyRule!!.resolve().name)
    }

    @Test
    fun serializeExplicitStress() {
        val word = graph.findOrAddWord("ea", q, null)
        word.setExplicitStress(1)
        val repo2 = graph.roundtrip()
        val word2 = repo2.wordsByText(repo2.languageByShortName("Q")!!, "ea").single()
        assertEquals(1, word2.stressedPhonemeIndex)
        assertEquals(true, word2.explicitStress)
    }

    @Test
    fun serializeCompound() {
        val baseWord = graph.addWord("mann", q, null)
        val prefix = graph.addWord("sæ", q, null)
        val compoundWord = graph.addWord("sæmann", q, null)
        graph.createCompound(compoundWord, listOf(prefix, baseWord), headIndex = 1)
        val repo2 = graph.roundtrip()
        val compoundWord2 = repo2.wordsByText(repo2.languageByShortName("Q")!!, "sæmann").single()
        val compound2 = repo2.findCompoundsByCompoundWord(compoundWord2).single()
        assertEquals(1, compound2.headIndex)
    }

    @Test
    fun serializeParadigmRules() {
        val paradigm = graph.addParadigm("Noun", q, listOf("N"))
        val preRule = graph.rule("word ends with 'a':\n- change ending to ''", name = "q-pre", fromLanguage = q)
        val postRule = graph.rule("word ends with 'oo':\n - change ending to 'o'", name = "q-post", fromLanguage = q)
        paradigm.preRule = preRule
        paradigm.postRule = postRule
        val repo2 = graph.roundtrip()
        assertEquals(1, repo2.paradigmsForLanguage(repo2.languageByShortName("Q")!!).size)
        assertEquals("q-pre", repo2.paradigmById(paradigm.id)!!.preRule!!.name)
        assertEquals("q-post", repo2.paradigmById(paradigm.id)!!.postRule!!.name)
    }

    @Test
    fun serializeLinkRuleSequence() {
        val seq = setupRuleSequence()
        val lai = graph.addWord("lai", language = ce, gloss = null)
        val la = graph.addWord("la", language = q, gloss = null)
        val link = graph.addLink(la, lai, Link.Origin)
        graph.applyRuleSequence(link, seq)
        val repo2 = graph.roundtrip()
        val la2 = repo2.wordById(la.id)!!
        val link2 = repo2.getLinksFrom(la2).single()
        assertEquals(seq.name, link2.sequence!!.name)
    }

    @Test
    fun serializeProtoLanguage() {
        val qLang = graph.languageByShortName("Q")!!
        qLang.protoLanguage = ce

        val repo2 = graph.roundtrip()
        val q2 = repo2.languageByShortName("Q")!!
        assertEquals("CE", q2.protoLanguage?.shortName)
    }

    @Test
    fun serializeAccentTypes() {
        val accentTypes = mutableSetOf(AccentType.Acute, AccentType.Circumflex)
        q.accentTypes = accentTypes
        val repo2 = graph.roundtrip()
        val q2 = repo2.languageByShortName("Q")!!
        assertEquals(accentTypes, q2.accentTypes)
    }

    @Test
    fun serializeSyllabographic() {
        val ht = graph.addLanguage("Hittite", "Ht")
        val word = graph.addWord("pé-ra-an", ht, gloss = null, syllabographic = true)
        val graph2 = graph.roundtrip()
        val word2 = graph2.wordById(word.id)!!
        assertEquals(true, word2.syllabographic)
    }

    @Test
    fun serializeLanguageSyllabographic() {
        val ht = graph.addLanguage("Hittite", "Ht")
        ht.syllabographic = true
        val graph2 = graph.roundtrip()
        val ht2 = graph2.languageByShortName("Ht")!!
        assertEquals(true, ht2.syllabographic)
    }
}
