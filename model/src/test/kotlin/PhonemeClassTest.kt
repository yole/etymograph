package page.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Test

class PhonemeClassTest : QBaseTest() {
    @Test
    fun diphthong() {
        val rule = parseRule(q, q, "* a > o if sound is not diphthong")
        assertEquals("ainu", rule.apply(q.word("ainu")).text)
        assertEquals("omo", rule.apply(q.word("ama")).text)
    }

    @Test
    fun diphthongSecondVowel() {
        val rule = parseRule(q, q, "* i > 0 if sound is not diphthong")
        assertEquals("ainu", rule.apply(q.word("ainu")).text)
        assertEquals("sla", rule.apply(q.word("sila")).text)
    }

    @Test
    fun stressedSoundCondition() {
        val rule = parseRule(q, q, "* o > a if previous sound is 'w' and sound is stressed")
        assertEquals("wawo", rule.apply(q.word("wowo").apply { setExplicitStress(1) }).text)
    }

    @Test
    fun stressedSoundCombinedCondition() {
        val text = "* w > x if next sound is stressed 'o'"
        val rule = parseRule(q, q, text)
        assertEquals("xowo", rule.apply(q.word("wowo").apply { setExplicitStress(1) }).text)
        assertEquals("wiwo", rule.apply(q.word("wiwo").apply { setExplicitStress(1) }).text)
        assertEquals(text, rule.toEditableText())
    }

    @Test
    fun stressedSoundConditionNegated() {
        val rule = parseRule(q, q, "* o > a if previous sound is 'w' and sound is not stressed")
        assertEquals("wowa", rule.apply(q.word("wowo").apply { setExplicitStress(1) }).text)
    }

    @Test
    fun unstressedSoundCondition() {
        val rule = parseRule(q, q, "* w > x if next sound is non-stressed 'o'")
        assertEquals("woxo", rule.apply(q.word("wowo").apply { setExplicitStress(1) }).text)
    }

    @Test
    fun stressedDiphthongCondition() {
        val rule = parseRule(q, q, "* e > i if previous vowel is stressed")
        assertEquals("lairi", rule.apply(q.word("laire").apply { setExplicitStress(1) }).text)
    }

    @Test
    fun wordFinal() {
        val rule = parseRule(q, q, "* a > 0 if next sound is word-final")
        assertEquals("glawr", rule.apply(q.word("glawar")).text)
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
        val it = q.word("it", "the")
        val morphemeInitialRule = q.rule(
            "* V > 0 if sound is morpheme-initial",
            name = "on-sound-deletion"
        )
        val rule = q.rule("- append morpheme 'it: the'\n- apply rule 'on-sound-deletion'")
        val hallæri = q.word("hallæri", "hallæri")
        val result = rule.apply(hallæri)
        assertEquals("hallærit", result.text)
    }

    @Test
    fun morphemeFinal() {
        val it = q.word("it", "the")
        q.rule(
            "* V > 0 if sound is morpheme-final and next sound is vowel",
            name = "on-sound-deletion"
        )
        val rule = q.rule("- append morpheme 'it: the'\n- apply rule 'on-sound-deletion'")
        val hallæri = q.word("hallæri", "hallæri")
        val result = rule.apply(hallæri)
        assertEquals("hallærit", result.text)
    }

    @Test
    fun morphemeInitialNotNormalized() {
        val it = q.word("it", "the")
        val stemRule = q.rule("word ends with 'r':\n- change ending to 'i'", name = "on-stem")
        val morphemeInitialRule = q.rule(
            "* V > 0 if sound is morpheme-initial and previous sound is vowel",
            name = "on-sound-deletion"
        )
        val rule =
            q.rule(
                "- apply rule 'on-stem'\n- append morpheme 'it: the'\n- apply rule 'on-sound-deletion'"
            )
        val hallæri = q.word("herr", "herr")
        val result = rule.apply(hallæri)
        assertEquals("herit", result.text)
    }

    @Test
    fun rootVowel() {
        val augment = q.word("e", "augment")
        val soundRule = q.rule("* e > o", name = "q-o-grade")
        val rule = q.rule("- prepend morpheme 'e: augment'\n- apply sound rule 'q-o-grade' to first root vowel")
        val hallæri = q.word("kes", "kes")
        val result = rule.apply(hallæri)
        assertEquals("ekos", result.text)
    }
}