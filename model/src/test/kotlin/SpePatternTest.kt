package ru.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Test

class SpePatternTest : QBaseTest() {
    @Test
    fun parseSimple() {
        val pattern = SpePattern.parse(q, q, "a -> o")
        val beforeNode = pattern.before.single() as SpeLiteralNode
        assertEquals("a", beforeNode.text)
        val afterNode = pattern.after.single() as SpeLiteralNode
        assertEquals("o", afterNode.text)
    }

    @Test
    fun parseContext() {
        val pattern = SpePattern.parse(q, q,"a -> o / b_c")
        val precede = pattern.preceding.single() as SpeLiteralNode
        assertEquals("b", precede.text)
        val follow = pattern.following.single()  as SpeLiteralNode
        assertEquals("c", follow.text)
    }

    @Test
    fun parseClass() {
        val pattern = SpePattern.parse(q, q, "a -> o / [+voice]_")
        val precede = pattern.preceding.single() as SpePhonemeClassNode
        assertEquals("+voice", precede.phonemeClass.name)
    }

    @Test
    fun parseMultipleClass() {
        val pattern = SpePattern.parse(q, q,"a -> o / [+voice,-sonorant,-continuant]_")
        val precede = pattern.preceding.single() as SpePhonemeClassNode
        val phonemeClass = precede.phonemeClass as IntersectionPhonemeClass
        assertEquals(3, phonemeClass.classList.size)
        assertEquals("a -> o / [+voice,-sonorant,-continuant]_", pattern.toString())
    }

    @Test
    fun parseNegatedClass() {
        val pattern = SpePattern.parse(q, q, "a -> o / [-coronal]_")
        val cls = (pattern.preceding.single() as SpePhonemeClassNode).phonemeClass
        assertEquals("coronal", (cls as NegatedPhonemeClass).baseClass.name)
        assertEquals("a -> o / [-coronal]_", pattern.toString())
    }

    @Test
    fun parsePlusBinaryClass() {
        val pattern = SpePattern.parse(q, q, "a -> o / [+sonorant]_")
        val cls = (pattern.preceding.single() as SpePhonemeClassNode).phonemeClass
        assertEquals("sonorant", cls.name)
        assertEquals("a -> o / [+sonorant]_", pattern.toString())
    }


    @Test
    fun parseEmptySet() {
        val pattern = SpePattern.parse(q, q,"a -> 0 / [+voice]_")
        assertEquals(0, pattern.after.size)
        assertEquals("a -> 0 / [+voice]_", pattern.toString())
    }

    @Test
    fun parseCV() {
        val pattern = SpePattern.parse(q, q, "a -> 0 / C_V")
        assertEquals("consonant", (pattern.preceding.single() as SpePhonemeClassNode).phonemeClass.name)
        assertEquals("vowel", (pattern.following.single() as SpePhonemeClassNode).phonemeClass.name)
        assertEquals("a -> 0 / C_V", pattern.toString())
    }

    @Test
    fun parsePhonemeInText() {
        val pattern = SpePattern.parse(q, q, "r -> hr / hr_hr")
        assertEquals("hr", (pattern.after.single() as SpeLiteralNode).text)
    }

    @Test
    fun parseAlternative() {
        val text = "i -> e / {a|o}_"
        val pattern = SpePattern.parse(q, q, text)
        val node = pattern.preceding.single() as SpeAlternativeNode
        assertEquals("a", (node.choices[0].single() as SpeLiteralNode).text)
        assertEquals("o", (node.choices[1].single() as SpeLiteralNode).text)
        assertEquals(text, pattern.toString())
    }

    @Test
    fun replaceWithoutContext() {
        val pattern = SpePattern.parse(q, q, "a -> o")
        assertEquals("bo", pattern.apply(q, "ba"))
    }

    @Test
    fun replaceMatchFollowing() {
        val pattern = SpePattern.parse(q, q, "a -> o / _d")
        assertEquals("bodak", pattern.apply(q, "badak"))
    }

    @Test
    fun replaceMatchPreceding() {
        val pattern = SpePattern.parse(q, q, "a -> o / b_")
        assertEquals("bodak", pattern.apply(q, "badak"))
    }

    @Test
    fun replaceMatchWordBoundaryForward() {
        val pattern = SpePattern.parse(q, q, "a -> o / _#")
        assertEquals("bado", pattern.apply(q, "bada"))
    }

    @Test
    fun replaceMatchWordBoundaryForwardPenultimate() {
        val pattern = SpePattern.parse(q, q, "a -> o / _#")
        assertEquals("bad", pattern.apply(q, "bad"))
    }

    @Test
    fun replaceMatchWordBoundaryBackward() {
        val pattern = SpePattern.parse(q, q, "a -> o / #_")
        assertEquals("obad", pattern.apply(q, "abad"))
    }

    @Test
    fun replaceMatchPhonemeClassForward() {
        val pattern = SpePattern.parse(q, q, "a -> o / _[+voice]")
        assertEquals("obat", pattern.apply(q, "abat"))
    }

    @Test
    fun replaceMultipleTokens() {
        val pattern = SpePattern.parse(q, q, "sr -> l / #_")
        assertEquals("lon", pattern.apply(q, "sron"))
    }

    @Test
    fun transform() {
        val pattern = SpePattern.parse(q, q, "[-sonorant,-continuant,+voice] -> [-voice] / _#")
        assertEquals("obat", pattern.apply(q,  "obad"))
    }

    @Test
    fun alternative() {
        val pattern = SpePattern.parse(q, q, "e -> i / _{a|o}")
        assertEquals("biabiobeu", pattern.apply(q, "beabeobeu"))
    }

    @Test
    fun alternativeBackwards() {
        val pattern = SpePattern.parse(q, q, "e -> i / {a|o}_")
        assertEquals("aiboibueb", pattern.apply(q, "aeboebueb"))
    }

    @Test
    fun alternativeBefore() {
        val pattern = SpePattern.parse(q, q, "{w|v} -> 0 / u_#")
        assertEquals("rau", pattern.apply(q, "rauw"))
    }

    @Test
    fun alternativeEndOfWord() {
        val pattern = SpePattern.parse(q, q, "m -> v / _{#|[-labial]}")
        assertEquals("ylv", pattern.apply(q, "ylm"))
    }

    @Test
    fun longContext() {
        val pattern = SpePattern.parse(q, q, "i -> e / _CC")
        assertEquals("dir", pattern.apply(q, "dir"))
    }

    @Test
    fun toStringSimple() {
        val pattern = SpePattern.parse(q, q, "a -> o")
        assertEquals("a -> o", pattern.toString())
    }

    @Test
    fun toStringContext() {
        val pattern = SpePattern.parse(q, q, "a -> o / b_c")
        assertEquals("a -> o / b_c", pattern.toString())
    }

    @Test
    fun toStringWordBoundary() {
        val pattern = SpePattern.parse(q, q, "a -> o / _#")
        assertEquals("a -> o / _#", pattern.toString())
    }

    @Test
    fun toStringPhonemeClass() {
        val pattern = SpePattern.parse(q, q, "a -> o / _[+voice]")
        assertEquals("a -> o / _[+voice]", pattern.toString())
    }

    @Test
    fun tooltip() {
        val pattern = SpePattern.parse(q, q, "m -> w / [nasal,-labial]_")
        val richText = pattern.toRichText()
        val fragment = richText.fragments.find { it.text.startsWith("nasal") }!!
        assertEquals("n", fragment.tooltip)
    }
}
