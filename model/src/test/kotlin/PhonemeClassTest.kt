package ru.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Test

class PhonemeClassTest : QBaseTest() {
    @Test
    fun diphthong() {
        val rule = parseRule(q, q, """
            sound is diphthong:
            - no change
            sound is 'a':
            - new sound is 'o'
        """.trimIndent())
        assertEquals("ainu", rule.apply(q.word("ainu"), emptyRepo).text)
        assertEquals("omo", rule.apply(q.word("ama"), emptyRepo).text)
    }

    @Test
    fun diphthongSecondVowel() {
        val rule = parseRule(q, q, """
            sound is 'i' and sound is not diphthong:
            - sound disappears
        """.trimIndent())
        assertEquals("ainu", rule.apply(q.word("ainu"), emptyRepo).text)
        assertEquals("sla", rule.apply(q.word("sila"), emptyRepo).text)
    }

    @Test
    fun stressedSoundCondition() {
        val rule = parseRule(q, q, """
            sound is 'o' and previous sound is 'w' and sound is stressed:
            - new sound is 'a'
        """.trimIndent())
        assertEquals("wawo", rule.apply(q.word("wowo").apply { stressedPhonemeIndex = 1 }, emptyRepo).text)
    }

    @Test
    fun stressedSoundCombinedCondition() {
        val rule = parseRule(q, q, """
            sound is stressed 'o' and previous sound is 'w':
            - new sound is 'a'
        """.trimIndent())
        assertEquals("wawo", rule.apply(q.word("wowo").apply { stressedPhonemeIndex = 1 }, emptyRepo).text)
        assertEquals("wiwo", rule.apply(q.word("wiwo").apply { stressedPhonemeIndex = 1 }, emptyRepo).text)
        assertEquals("sound is stressed 'o' and previous sound is 'w'", rule.logic.branches[0].condition.toEditableText())
        assertEquals("stressed 'o' > 'a' after 'w'", rule.toSummaryText(emptyRepo))
    }

    @Test
    fun stressedSoundConditionNegated() {
        val rule = parseRule(q, q, """
            sound is 'o' and previous sound is 'w' and sound is not stressed:
            - new sound is 'a'
        """.trimIndent())
        assertEquals("wowa", rule.apply(q.word("wowo").apply { stressedPhonemeIndex = 1 }, emptyRepo).text)
    }

    @Test
    fun unstressedSoundCondition() {
        val rule = parseRule(q, q, """
            sound is non-stressed 'o' and previous sound is 'w':
            - new sound is 'a'
        """.trimIndent())
        assertEquals("wowa", rule.apply(q.word("wowo").apply { stressedPhonemeIndex = 1 }, emptyRepo).text)
    }

    @Test
    fun stressedDiphthongCondition() {
        val rule = parseRule(q, q, """
            sound is 'e' and previous vowel is stressed:
            - new sound is 'i'
        """.trimIndent())
        assertEquals("lairi", rule.apply(q.word("laire").apply { stressedPhonemeIndex = 1 }, emptyRepo).text)
    }

    @Test
    fun wordFinal() {
        val rule = parseRule(q, q, "* a > 0 if next sound is word-final")
        assertEquals("glawr", rule.apply(q.word("glawar"), emptyRepo).text)
    }

    @Test
    fun nonWordInitial() {
        val rule = parseRule(q, q, "* w > 0 / _i if sound is non-word-initial")
        assertEquals("wii", applyRule(rule, q.word("wiwi")))
    }

    @Test
    fun syllableFinal() {
        val rule = parseRule(q, q, "* n > m if sound is syllable-final")
        assertEquals("inomdem", applyRule(rule, q.word("inonden")))
    }

    @Test
    fun nucleus() {
        val rule = parseRule(q, q, "* i > j if sound is nucleus")
        assertEquals("jkjukuik", applyRule(rule, q.word("ikiukuik")))
    }

    @Test
    fun morphemeInitial() {
        val repo = InMemoryGraphRepository().with(q)
        val it = repo.addWord("it", "the", language = q)
        val morphemeInitialRule = repo.rule("sound is morpheme-initial vowel:\n- sound disappears",
            fromLanguage = q, name = "on-sound-deletion")
        val rule = repo.rule("- append morpheme 'it: the'\n- apply rule 'on-sound-deletion'", fromLanguage = q)
        val hallæri = repo.addWord("hallæri", language = q)
        val result = rule.apply(hallæri, repo)
        assertEquals("hallærit", result.text)
    }

    @Test
    fun morphemeFinal() {
        val repo = InMemoryGraphRepository().with(q)
        val it = repo.addWord("it", "the", language = q)
        repo.rule("sound is morpheme-final vowel and next sound is vowel:\n- sound disappears",
            fromLanguage = q, name = "on-sound-deletion")
        val rule = repo.rule("- append morpheme 'it: the'\n- apply rule 'on-sound-deletion'", fromLanguage = q)
        val hallæri = repo.addWord("hallæri", language = q)
        val result = rule.apply(hallæri, repo)
        assertEquals("hallærit", result.text)
    }

    @Test
    fun morphemeInitialNotNormalized() {
        val repo = InMemoryGraphRepository().with(q)
        val it = repo.addWord("it", "the", language = q)
        val stemRule = repo.rule("word ends with 'r':\n- change ending to 'i'", fromLanguage = q, name = "on-stem")
        val morphemeInitialRule = repo.rule("sound is morpheme-initial vowel and previous sound is vowel:\n- sound disappears",
            fromLanguage = q, name = "on-sound-deletion")
        val rule = repo.rule("- apply rule 'on-stem'\n- append morpheme 'it: the'\n- apply rule 'on-sound-deletion'", fromLanguage = q)
        val hallæri = repo.addWord("herr", language = q)
        val result = rule.apply(hallæri, repo)
        assertEquals("herit", result.text)
    }

}