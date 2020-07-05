package ru.yole.etymograph

class Link(
    val fromWord: Word,
    val toWord: Word,
    val type: String,
    val rule: Rule?,
    source: String?,
    notes: String?
) : LangEntity(source, notes) {
    companion object {
        val Derived = "derived from"
        val Agglutination = "agglutination of"
    }
}
