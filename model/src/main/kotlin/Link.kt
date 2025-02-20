package ru.yole.etymograph

data class LinkType(val id: String, val name: String, val reverseName: String)

class Link(
    val fromEntity: LangEntity,
    val toEntity: LangEntity,
    val type: LinkType,
    var rules: List<Rule>,
    var sequence: RuleSequence?,
    var source: List<SourceRef>,
    var notes: String?
) {
    companion object {
        val Derived = LinkType(">", "Lemma", "Inflected forms")
        val Origin = LinkType("^", "originates from", "Words originating from this one")
        val Related = LinkType("~", "Related to", "Related to")
        val Variation = LinkType("=", "Variation of", "Variations")

        val allLinkTypes = listOf(Derived, Origin, Related, Variation)
    }

    fun applyRules(word: Word, graph: GraphRepository): Word {
        return rules.fold(word) { w, r -> r.apply(w, graph) }
    }
}

class Compound(
    id: Int,
    val compoundWord: Word,
    val components: MutableList<Word>,
    var headIndex: Int? = null,
    source: List<SourceRef>,
    notes: String?
) : LangEntity(id, source, notes) {
    fun isDerivation(): Boolean =
        components.any { c -> c.pos == KnownPartsOfSpeech.preverb.abbreviation || c.pos == KnownPartsOfSpeech.affix.abbreviation }

    fun headComponent(): Word? {
        return headIndex?.let { components.getOrNull(it) }
    }
}
