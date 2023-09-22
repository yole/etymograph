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

        val json = repo.toJson()
        val repo2 = JsonGraphRepository.fromJsonString(json)
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
        """.trimIndent(), q.parseContext()), null, null, null, null, emptyList(), null
        )

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
}
