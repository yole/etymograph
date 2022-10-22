package ru.yole.etymograph

data class LinkType(val id: String, val name: String)

open class Link(
    val fromWord: Word,
    val toWord: Word,
    val type: LinkType,
    val rule: Rule?,
    source: String?,
    notes: String?
) : LangEntity(source, notes) {
    companion object {
        val Derived = LinkType(">", "derived from")
        val Agglutination = LinkType("+", "agglutination of")

        val allLinkTypes = listOf(Derived, Agglutination)
    }
}
