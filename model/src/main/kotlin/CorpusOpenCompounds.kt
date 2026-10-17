package page.yole.etymograph

import kotlin.math.abs

data class CorpusOpenCompoundCandidate(
    val compound: OpenCompound,
    val associations: List<CorpusWordAssociation>
)

fun CorpusText.findOpenCompoundCandidates(index: Int): List<CorpusOpenCompoundCandidate> {
    val corpusWords = mapToLines().flatMap { it.corpusWords }
    require(corpusWords.any { it.index == index }) { "Word index $index is out of bounds" }
    val associationsByIndex = words.associateBy { it.index }
    return language.graph.allOpenCompounds().mapNotNull { compound ->
        val componentIndices = compound.components.mapIndexed { i, word -> word.id to i }.toMap()
        val componentCache = mutableMapOf<Int, Int>()
        val matches = List(compound.components.size) { mutableListOf<CorpusWordAssociation>() }
        for (corpusWord in corpusWords) {
            if (corpusWord.openCompound != null && corpusWord.openCompound.id != compound.id) continue
            val possibleWords = corpusWord.word?.let { listOf(it) } ?: corpusWord.wordCandidates.orEmpty()
            for (word in possibleWords) {
                val componentIndex = componentCache.getOrPut(word.id) {
                    val path = compound.findComponentPath(word)
                    if (path == null) -1 else componentIndices.getValue(path.lastOrNull()?.toEntity?.id ?: word.id)
                }
                if (componentIndex >= 0) {
                    matches[componentIndex].add(CorpusWordAssociation(corpusWord.index, word,
                        associationsByIndex[corpusWord.index]?.contextGloss, compound))
                }
            }
        }
        findOpenCompoundOccurrences(compound, matches, index)?.let {
            CorpusOpenCompoundCandidate(compound, it)
        }
    }
}

private fun findOpenCompoundOccurrences(
    compound: OpenCompound,
    matches: List<List<CorpusWordAssociation>>,
    index: Int,
    requireComplete: Boolean = true
): List<CorpusWordAssociation>? {
    val heads = matches[compound.headIndex].sortedWith(
        compareBy<CorpusWordAssociation> { abs(it.index - index) }.thenBy { it.index }
    )
    for (head in heads) {
        for ((anchorComponentIndex, occurrences) in matches.withIndex()) {
            for (anchor in occurrences.filter { it.index == index }) {
                fun fitsAnchor(occurrence: CorpusWordAssociation, componentIndex: Int): Boolean = when {
                    componentIndex < anchorComponentIndex -> occurrence.index < anchor.index
                    componentIndex > anchorComponentIndex -> occurrence.index > anchor.index
                    else -> occurrence.index == anchor.index && occurrence.word.id == anchor.word.id
                }
                if (!fitsAnchor(head, compound.headIndex)) continue
                val selected = MutableList<CorpusWordAssociation?>(matches.size) { null }
                selected[compound.headIndex] = head
                val previousHead = if (requireComplete) Int.MIN_VALUE
                    else heads.filter { it.index < head.index }.maxOfOrNull { it.index } ?: Int.MIN_VALUE
                val nextHead = if (requireComplete) Int.MAX_VALUE
                    else heads.filter { it.index > head.index }.minOfOrNull { it.index } ?: Int.MAX_VALUE
                // Work outward from the head while keeping the clicked occurrence fixed.
                var boundary = head.index
                for (i in compound.headIndex - 1 downTo 0) {
                    val occurrence = matches[i].filter { it.index > previousHead && it.index < boundary && fitsAnchor(it, i) }
                        .maxByOrNull { it.index }
                    if (occurrence == null) {
                        if (requireComplete) break else continue
                    }
                    selected[i] = occurrence
                    boundary = occurrence.index
                }
                boundary = head.index
                for (i in compound.headIndex + 1 until matches.size) {
                    val occurrence = matches[i].filter { it.index > boundary && it.index < nextHead && fitsAnchor(it, i) }
                        .minByOrNull { it.index }
                    if (occurrence == null) {
                        if (requireComplete) break else continue
                    }
                    selected[i] = occurrence
                    boundary = occurrence.index
                }
                if (selected[anchorComponentIndex]?.index == anchor.index &&
                    (!requireComplete || selected.all { it != null })) return selected.filterNotNull()
            }
        }
    }
    return null
}

fun CorpusText.associateOpenCompound(index: Int, compound: OpenCompound) {
    val candidate = findOpenCompoundCandidates(index).firstOrNull { it.compound.id == compound.id }
        ?: throw IllegalArgumentException("The open compound cannot be associated with this word in the current text")
    for (association in candidate.associations) {
        associateWord(association.index, association.word, association.contextGloss, compound)
    }
}

fun CorpusText.removeOpenCompoundAssociation(index: Int, compound: OpenCompound) {
    val clicked = words.firstOrNull { it.index == index }
    require(clicked?.openCompound?.id == compound.id) { "This word is not linked to the open compound" }
    val componentIndices = compound.components.mapIndexed { i, word -> word.id to i }.toMap()
    val matches = List(compound.components.size) { mutableListOf<CorpusWordAssociation>() }
    for (association in words) {
        if (association.openCompound?.id != compound.id) continue
        val path = compound.findComponentPath(association.word) ?: continue
        val componentIndex = componentIndices.getValue(path.lastOrNull()?.toEntity?.id ?: association.word.id)
        matches[componentIndex].add(association)
    }
    val occurrences = findOpenCompoundOccurrences(compound, matches, index, requireComplete = false)
        ?: listOfNotNull(clicked)
    for (association in occurrences) {
        association.openCompound = null
    }
}
