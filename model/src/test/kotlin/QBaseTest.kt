package page.yole.etymograph

open class QBaseTest {
    val graph = InMemoryGraph()

    val q = quenya(graph)
    val v = q.phonemeClassByName("vowel")!!

    val ce = graph.addLanguage("Common Eldarin", "CE").also {
        it.phonemes = listOf(
            Phoneme(-1, listOf("kh"), null, setOf("voiceless", "consonant")),
            Phoneme(-1, listOf("th"), null, setOf("voiceless", "consonant"))
        ) + listOf("a", "o", "u", "i").map { p ->
            Phoneme(-1, listOf(p), null, setOf("short", "vowel"))
        }
    }

    fun Graph.addWord(
        text: String,
        gloss: String? = text,
        pos: String? = null,
        classes: List<String> = emptyList(),
        language: Language = q
    ) = findOrAddWord(text, language, gloss, pos = pos, classes = classes)

    fun applyRule(rule: Rule, word: Word): String {
        return rule.apply(word).text
    }
}

fun quenya(graph: Graph): Language = graph.addLanguage("Quenya", "Q").also {
    it.phonemes = listOf(
        Phoneme(-1, listOf("e", "ë"), null, setOf("vowel"))
    ) + listOf("a", "o", "u", "i").map { p ->
        Phoneme(-1, listOf(p), null, setOf("short", "vowel"))
    } + listOf("á", "ó", "ú", "í", "é").map { p ->
        Phoneme(-1, listOf(p), null, setOf("long", "vowel"))
    } + listOf("c", "h", "l", "q", "r", "w", "x", "z").map { p ->
        Phoneme(-1, listOf(p), null, setOf("consonant"))
    } + Phoneme(-1, listOf("y"), "j", setOf("consonant")) +
            listOf("p", "t", "b", "d", "f", "m", "n", "k", "g", "s").map { p -> phoneme(p) } +
            phoneme("hr", "voiceless alveolar trill consonant")

    it.diphthongs = listOf("ai", "oi", "ui", "au", "eu", "iu")
}
