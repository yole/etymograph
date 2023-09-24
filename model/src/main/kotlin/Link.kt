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
}

class Compound(
    id: Int,
    val compound: Word,
    val components: MutableList<Word>,
    source: List<SourceRef>,
    notes: String?
) : LangEntity(id, source, notes)
