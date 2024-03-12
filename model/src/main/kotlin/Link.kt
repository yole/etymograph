package ru.yole.etymograph

data class LinkType(val id: String, val name: String, val reverseName: String)

class Link(
    val fromEntity: LangEntity,
    val toEntity: LangEntity,
    val type: LinkType,
    var rules: List<Rule>,
    var source: List<SourceRef>,
    var notes: String?
) {
    companion object {
        val Derived = LinkType(">", "derived from", "Words derived from this one")
        val Related = LinkType("~", "Related to", "Related to")
        val Variation = LinkType("=", "Variation of", "Variations")

        val allLinkTypes = listOf(Derived, Related, Variation)
    }

    fun applyRules(word: Word, graph: GraphRepository): Word {
        return rules.fold(word) { w, r -> r.apply(w, graph) }
    }
}

class Compound(
    id: Int,
    val compoundWord: Word,
    val components: MutableList<Word>,
    source: List<SourceRef>,
    notes: String?
) : LangEntity(id, source, notes)
