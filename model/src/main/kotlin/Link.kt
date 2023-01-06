package ru.yole.etymograph

data class LinkType(val id: String, val name: String, val reverseName: String)

open class Link(
    val fromWord: Word,
    val toWord: Word,
    val type: LinkType,
    var rules: List<Rule>,
    source: String?,
    notes: String?
) : LangEntity(source, notes) {
    companion object {
        val Derived = LinkType(">", "derived from", "Words derived from this one")
        val Agglutination = LinkType("+", "compound of", "Part of compound words")
        val Related = LinkType("~", "Related to", "Related to")

        val allLinkTypes = listOf(Derived, Agglutination, Related)
    }
}
