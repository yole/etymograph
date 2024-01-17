package ru.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.yole.etymograph.JsonGraphRepository.Companion.ruleBranchesFromSerializedFormat
import ru.yole.etymograph.JsonGraphRepository.Companion.ruleToSerializedFormat

class JsonGraphRepositoryTest : QBaseTest() {
    @Test
    fun deletedWords() {
        val repo = JsonGraphRepository(null)
        repo.addLanguage(q)

        val abc = repo.addWord("abc")
        val def = repo.addWord("def")
        repo.deleteWord(abc)

        val repo2 = repo.roundtrip()
        assertEquals(null, repo2.wordById(abc.id))
        assertEquals("def", repo2.wordById(def.id)!!.text)
    }

    @Test
    fun serializeApplySoundRule() {
        val repo = JsonGraphRepository(null)
        val soundRule = repo.addRule(
            "q-lengthen", q, q,
            Rule.parseBranches("""
            sound is 'a':
            - new sound is 'á'
        """.trimIndent(), q.parseContext()))

        val parseContext = RuleParseContext(q, q) {
            if (it == "q-lengthen") RuleRef.to(soundRule) else throw RuleParseException("no such rule")
        }
        val applySoundRule = Rule(-1, "lengthen-first-vowel", q, q, Rule.parseBranches("""
            - apply sound rule 'q-lengthen' to first vowel
        """.trimIndent(), parseContext), null, null, null, null, emptyList(), null)

        val serializedData = applySoundRule.ruleToSerializedFormat()
        assertEquals(2, serializedData.branches[0].instructions[0].args.size)

        val branches = ruleBranchesFromSerializedFormat(repo, q, serializedData.branches)
        val insn = branches[0].instructions[0] as ApplySoundRuleInstruction
        assertEquals("q-lengthen", insn.ruleRef.resolve().name)
        assertEquals("vowel", insn.seekTarget.phonemeClass.name)
    }

    @Test
    fun serializeRelativePhonemeRule() {
        val repo = JsonGraphRepository(null)
        val rule = parseRule(q, q, """
            sound is 'i' and previous sound is not vowel:
            - sound disappears
        """.trimIndent())

        val serializedData = rule.ruleToSerializedFormat()
        val branches = ruleBranchesFromSerializedFormat(repo, q, serializedData.branches)
        assertEquals("sound is 'i' and previous sound is not vowel", branches[0].condition.toEditableText())
    }

    @Test
    fun serializeTranslation() {
        val repo = JsonGraphRepository(null)
        repo.addLanguage(q)
        val corpusText = repo.addCorpusText("abc", null, q)
        repo.addTranslation(corpusText, "def", emptyList())
        val repo2 = repo.roundtrip()
        val corpusText2 = repo2.corpusTextById(corpusText.id)!!
        assertEquals(1, repo2.translationsForText(corpusText2).size)
    }

    private fun JsonGraphRepository.roundtrip(): JsonGraphRepository {
        val json = toJson()
        return JsonGraphRepository.fromJsonString(json)
    }

    @Test
    fun serializeDeleteParadigm() {
        val repo = JsonGraphRepository(null)
        repo.addLanguage(q)
        val np = repo.addParadigm("Noun", q, "N")
        val vp = repo.addParadigm("Verb", q, "V")
        repo.deleteParadigm(np)
        assertEquals(1, repo.allParadigms().size)
        val repo2 = repo.roundtrip()
        assertEquals(1, repo2.allParadigms().size)
        assertEquals("Verb", repo2.paradigmById(vp.id)!!.name)
    }

    @Test
    fun serializePhonemes() {
        val repo = JsonGraphRepository(null)
        repo.addLanguage(q)
        q.phonemes = mutableListOf(Phoneme(listOf("a"), listOf("front", "open", "vowel")))
        val repo2 = repo.roundtrip()
        assertEquals(1, repo2.languageByShortName("Q")!!.phonemes.size)
    }
}
