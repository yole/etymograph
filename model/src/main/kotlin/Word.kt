package ru.yole.etymograph

class Word(
    val id: Int,
    val text: String,
    val language: Language,
    val gloss: String? = null,
    source: String? = null,
    notes: String? = null
) : LangEntity(source, notes) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Word

        if (text != other.text) return false
        if (language != other.language) return false
        if (gloss != other.gloss) return false

        return true
    }

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + language.hashCode()
        result = 31 * result + (gloss?.hashCode() ?: 0)
        return result
    }

    fun getOrComputeGloss(graph: GraphRepository): String? {
        gloss?.let { return it }
        val components = graph.getLinksFrom(this).filter { it.type == Link.Agglutination }
        if (components.isNotEmpty()) {
            return components.joinToString("-") {
                it.toWord.getOrComputeGloss(graph)?.substringBefore(", ") ?: "?"
            }
        }
        val derivation = graph.getLinksFrom(this).filter { it.type == Link.Derived }.singleOrNull()
        val addedCategories = derivation?.rule?.addedCategories
        if (addedCategories != null) {
            derivation.toWord.getOrComputeGloss(graph)?.let { fromGloss ->
                return "$fromGloss$addedCategories"
            }
        }
        return null
    }
}
