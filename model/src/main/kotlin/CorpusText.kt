package page.yole.etymograph

import java.util.*

class CorpusWord(
    val index: Int,
    val text: String,
    val normalizedText: String,
    val segmentedText: String,
    val syllabogramSequence: SyllabogramSequence?,
    val word: Word?,
    val wordCandidates: List<Word>?,
    val gloss: String?,
    val contextGloss: String?,
    val segmentedGloss: String?,
    val stressIndex: Int?,
    val stressLength: Int?,
    val glossable: Boolean,
    val homonym: Boolean,
    val openCompound: OpenCompound? = null,
)

class CorpusTextLine(val corpusWords: List<CorpusWord>, val lineNumber: String? = null, val separator: Boolean = false)

class CorpusWordAssociation(
    val index: Int,
    var word: Word,
    var contextGloss: String? = null,
    var openCompound: OpenCompound? = null
) {
    fun getOrComputeGloss(): String? {
        val compound = openCompound ?: return word.getOrComputeGloss()
        val path = compound.findComponentPath(word) ?: return null
        val componentId = path.lastOrNull()?.toEntity?.id ?: word.id
        val baseGloss = if (componentId == compound.headComponent().id) compound.gloss else "..."
        return baseGloss?.let { gloss ->
            path.asReversed().fold(gloss) { result, link ->
                val derivedWord = link.fromEntity as Word
                link.rules.fold(result) { ruleGloss, rule ->
                    rule.applyCategories(ruleGloss, derivedWord.segments.any { it.sourceRule == rule })
                }
            }
        }
    }
}

private val openCompoundAssociationLinkTypes = setOf(Link.Derived, Link.Variation, Link.Transcription)

internal fun OpenCompound.findComponentPath(word: Word): List<Link>? {
    val componentIds = components.map { it.id }.toSet()
    val visited = mutableSetOf(word.id)
    val queue = ArrayDeque<Pair<Word, List<Link>>>()
    queue.add(word to emptyList())
    while (queue.isNotEmpty()) {
        val (candidate, path) = queue.removeFirst()
        if (candidate.id in componentIds) return path
        for (link in word.graph.getLinksFrom(candidate)) {
            if (link.type !in openCompoundAssociationLinkTypes) continue
            val base = link.toEntity as? Word ?: continue
            if (visited.add(base.id)) {
                queue.add(base to (path + link))
            }
        }
    }
    return null
}

class CorpusText(
    id: Int,
    text: String,
    var title: String?,
    val language: Language,
    words: List<CorpusWordAssociation> = emptyList(),
    source: List<SourceRef> = emptyList(),
    notes: String? = null
): LangEntity(id, source, notes) {
    private data class WordText(val baseText: String, val normalizedText: String, val index: Int)

    private val _words = words.toMutableList()
    private var _text: String = text

    val words: List<CorpusWordAssociation>
        get() = _words

    fun wordByIndex(index: Int): Word? {
        return _words.find { it.index == index }?.word
    }

    var text: String
        get() = _text
        set(value) {
            updateText(value)
        }

    fun updateText(value: String): Map<Int, Int> {
        val oldWords = iterateWords().toList()
        val oldWordMap = mutableMapOf<String, MutableList<WordText>>()
        val oldAssociations = _words.associateBy { it.index }
        for (word in oldWords) {
            oldWordMap.getOrPut(word.normalizedText) { arrayListOf() }.add(word)
        }

        _text = value
        val newWords = iterateWords().toList()

        _words.clear()
        val oldToNewWordIndices = mutableMapOf<Int, Int>()
        for (word in newWords) {
            val list = oldWordMap[word.normalizedText]
            if (!list.isNullOrEmpty()) {
                val oldWord = list.removeAt(0)
                oldToNewWordIndices[oldWord.index] = word.index
                val assocWord = oldAssociations[oldWord.index]
                if (assocWord != null) {
                    _words.add(CorpusWordAssociation(word.index, assocWord.word, assocWord.contextGloss, assocWord.openCompound))
                }
            }
        }

        return oldToNewWordIndices
    }

    fun mapToLines(): List<CorpusTextLine> {
        var currentIndex = 0
        var sentenceStart = true
        return text.split("\n").map { line ->
            if (isSeparator(line)) return@map CorpusTextLine(emptyList(), separator = true)
            val (lineNumber, tail) = if ('#' in line)
                line.substringBefore('#').trim() to line.substringAfter('#').trim()
            else
                null to line

            val textWords = splitIntoNormalizedWords(tail, currentIndex)
            CorpusTextLine(textWords.map { tw ->
                val assoc = words.find { it.index == currentIndex }
                val word = assoc?.word
                val normalizedText = if (sentenceStart || leadingPunctuation.any { tw.baseText.startsWith(it) })
                    tw.normalizedText
                else
                    restoreCase(tw.normalizedText, tw.baseText)
                sentenceStart = sentenceEndPunctuation.any { tw.baseText.endsWith(it) }
                val syllabogramSequence = if (language.syllabographic) TlhDigSyllabogramSyntax.parse(tw.baseText) else null
                if (word != null) {
                    val stressData = if (language.accentTypes.isEmpty()) word.calculateStress() else null
                    val leadingPunctuation = tw.baseText.takeWhile { it in leadingPunctuation }
                    val trailingPunctuation = tw.baseText.takeLastWhile { it in punctuation }
                    val segmentedText = leadingPunctuation + restoreCase(word.segmentedText(), tw.baseText) + trailingPunctuation
                    val openCompound = assoc.openCompound
                    val gloss = assoc.getOrComputeGloss()
                    val stressIndex = adjustStressIndex(word, stressData?.index)?.plus(leadingPunctuation.length)

                    CorpusWord(currentIndex++, tw.baseText, normalizedText, segmentedText, syllabogramSequence, word, null,
                        gloss,
                        assoc.contextGloss,
                        gloss, stressIndex, stressData?.length, true, word.graph.isHomonym(word), openCompound)
                }
                else {
                    val wordCandidates = language.graph.wordsByText(language, tw.normalizedText, language.syllabographic)
                    val gloss = wordCandidates.firstOrNull()?.getOrComputeGloss()
                    CorpusWord(currentIndex++, tw.baseText, normalizedText, tw.baseText, syllabogramSequence,null, wordCandidates,
                        gloss, null, gloss, null, null,
                        glossable = isWordGlossable(tw),
                        homonym = false)
                }
            }, lineNumber)
        }
    }

    private fun isWordGlossable(tw: WordText): Boolean {
        if (language.syllabographic && 'x' in tw.baseText) {
            return false
        }
        return tw.baseText.any { it !in innerPunctuation && it !in punctuation && !it.isDigit() }
    }

    private fun adjustStressIndex(wordWithSegments: Word?, stressIndex: Int?): Int? {
        if (stressIndex == null) return null
        val segments = wordWithSegments?.cachedSegments ?: return stressIndex
        var result = stressIndex
        for (segment in segments) {
            if (segment.firstCharacter > 0 && segment.firstCharacter <= stressIndex) {
                result++
            }
        }
        return result
    }

    private fun splitIntoNormalizedWords(line: String, lineStartIndex: Int): List<WordText> {
        var currentIndex = lineStartIndex
        val trimmed = line.trim()
        if (trimmed.isEmpty()) {
            return emptyList()
        }
        return trimmed.split(' ').map {
            val cleanText = removePunctuation(it)
            WordText(it,
                if (language.syllabographic) cleanText else language.normalizeWord(cleanText, normalizeGraphemes = false),
                currentIndex++
            )
        }
    }

    fun containsWord(word: Word): Boolean {
        if (_words.any { it.word.id == word.id }) return true
        for (wordText in iterateWords()) {
            if (wordText.normalizedText == word.text && wordByIndex(wordText.index) == null) return true
        }
        return false
    }

    fun attestationLineNumbers(word: Word): List<String> {
        val indices = iterateWords().filter { wordText ->
            val associatedWord = wordByIndex(wordText.index)
            associatedWord?.id == word.id || (associatedWord == null && wordText.normalizedText == word.text)
        }.map { it.index }.toSet()
        return mapToLines().filter { line -> line.corpusWords.any { it.index in indices } }
            .mapNotNull { it.lineNumber?.takeIf { number -> number.isNotBlank() } }.distinct()
    }

    fun wordCount(): Int = iterateWords().count()

    fun attestedForms(word: Word): Map<String, List<String>> {
        val result = linkedMapOf<String, MutableSet<String>>()
        for (line in mapToLines()) {
            for (corpusWord in line.corpusWords) {
                val cleanText = removePunctuation(corpusWord.text)
                val normalizedText = if (language.syllabographic) cleanText
                    else language.normalizeWord(cleanText, normalizeGraphemes = false)
                if (corpusWord.word?.id != word.id &&
                    !(corpusWord.word == null && normalizedText == word.text)) continue
                val spelling = corpusWord.text.trimStart(*leadingPunctuation).trimEnd(*punctuation)
                val lineNumbers = result.getOrPut(spelling) { linkedSetOf() }
                line.lineNumber?.takeIf { it.isNotBlank() }?.let { lineNumbers.add(it) }
            }
        }
        return result.mapValues { it.value.toList() }
    }

    private fun iterateWords(): Sequence<WordText> {
        var currentIndex = 0
        return sequence {
            for (line in text.split('\n')) {
                if (isSeparator(line)) continue
                val tail = line.substringAfter('#', line).trim()
                for (wordText in splitIntoNormalizedWords(tail, currentIndex)) {
                    currentIndex++
                    yield(wordText)
                }
            }
        }
    }

    private fun isSeparator(line: String): Boolean = line.all { it == '-' } && line.length >= 3

    fun normalizedWordTextAt(index: Int) = iterateWords().elementAt(index).normalizedText

    fun associateWord(index: Int, word: Word, contextGloss: String? = null, openCompound: OpenCompound? = null) {
        require(openCompound == null || openCompound.findComponentPath(word) != null) {
            "The associated word must be a member of the open compound or derived from one through Derived, Variation or Transcription links"
        }
        for (assoc in _words) {
            if (assoc.index == index) {
                assoc.word = word
                assoc.contextGloss = contextGloss
                assoc.openCompound = openCompound
                return
            }
        }
        _words.add(CorpusWordAssociation(index, word, contextGloss, openCompound))
    }

    fun removeWord(word: Word) {
        _words.removeIf { it.word == word }
    }

    fun lockWordAssociations() {
        for (w in iterateWords()) {
            if (wordByIndex(w.index) == null) {
                val candidate = language.graph.wordsByText(language, w.normalizedText).singleOrNull()
                if (candidate != null) {
                    _words.add(CorpusWordAssociation(w.index, candidate, null))
                }
            }
        }
    }

    companion object {
        const val leftHalfBracket = '⸢'
        const val rightHalfBracket = '⸣'
        val punctuation = charArrayOf('!', ',', '.', '?', ':', ';', '\"', '\'', '(', ')', '|', '·', '〈', '〉')
        val sentenceEndPunctuation = charArrayOf('.', '?', '!')
        val leadingPunctuation = charArrayOf('\"', '(', '\'', '〈')
        val innerPunctuation = arrayOf('[', ']', leftHalfBracket, rightHalfBracket, '〈', '〉')
    }
}

fun removePunctuation(string: String): String {
    var cleanText = string.trimStart(*CorpusText.leadingPunctuation).trimEnd(*CorpusText.punctuation)
    for (c in CorpusText.innerPunctuation) {
        cleanText = cleanText.replace(c.toString(), "")
    }
    return cleanText
}

fun restoreCase(normalizedText: String, baseText: String): String {
    val offset = baseText.takeWhile { it == '"' }.length
    return buildString {
        for ((i, c) in normalizedText.withIndex()) {
            if (i + offset < baseText.length && baseText[i + offset].isUpperCase()) {
                append(c.uppercase(Locale.FRANCE))
            }
            else {
                append(c)
            }
        }
    }
}

class Translation(
    id: Int,
    val corpusText: CorpusText,
    var text: String,
    source: List<SourceRef>,
    notes: String?,
    var anchorStartIndex: Int? = null,
    var anchorEndIndex: Int? = null
) : LangEntity(id, source, notes) {
    fun remapAnchor(oldToNewWordIndices: Map<Int, Int>) {
        val start = anchorStartIndex ?: return
        val end = anchorEndIndex ?: return
        if (start >= end) {
            anchorStartIndex = null
            anchorEndIndex = null
            return
        }

        val mappedWordIndices = (start until end).mapNotNull { oldToNewWordIndices[it] }
        if (mappedWordIndices.isEmpty()) {
            anchorStartIndex = null
            anchorEndIndex = null
            return
        }

        anchorStartIndex = mappedWordIndices.minOrNull()
        anchorEndIndex = mappedWordIndices.maxOrNull()?.plus(1)
    }
}
