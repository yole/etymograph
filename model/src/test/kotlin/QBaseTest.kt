package page.yole.etymograph

open class QBaseTest {
    val graph = InMemoryGraph()

    val q = quenya(graph)
    val v = q.phonemeClassByName("vowel")!!

    val ce = graph.addLanguage("Common Eldarin", "CE").also {
        it.phonemes = listOf(
            Phoneme(-1, listOf("kh"), sound = null, classes = setOf("voiceless", "consonant")),
            Phoneme(-1, listOf("th"), sound = null, classes = setOf("voiceless", "consonant"))
        ) + listOf("a", "o", "u", "i").map { p ->
            Phoneme(-1, listOf(p), sound = null, classes = setOf("short", "vowel"))
        }
    }

    fun applyRule(rule: Rule, word: Word): String {
        return rule.apply(word).text
    }
}

fun quenya(graph: Graph): Language = graph.addLanguage("Quenya", "Q").also {
    it.phonemes = listOf(
        Phoneme(-1, listOf("e", "ë"), sound = null, classes = setOf("vowel"))
    ) + listOf("a", "o", "u", "i").map { p ->
        Phoneme(-1, listOf(p), sound = null, classes = defaultPhonemeClasses[p]!! + "short")
    } + listOf("á", "ó", "ú", "í", "é").map { p ->
        Phoneme(-1, listOf(p), sound = null, classes = defaultPhonemeClasses[p.removeDiacritics()]!! + "long")
    } + listOf("c", "h", "l", "q", "r", "w", "x", "z").map { p ->
        Phoneme(-1, listOf(p), sound = null, classes = setOf("consonant"))
    } + Phoneme(-1, listOf("y"), sound = "j", classes = setOf("consonant")) +
            listOf("p", "t", "b", "d", "f", "m", "n", "k", "g", "s").map { p -> phoneme(p) } +
            phoneme("hr", "voiceless alveolar trill consonant")

    it.diphthongs = listOf("ai", "oi", "ui", "au", "eu", "iu")
}
