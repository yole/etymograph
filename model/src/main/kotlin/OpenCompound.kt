package page.yole.etymograph

class OpenCompound(
    id: Int,
    components: List<Word>,
    headIndex: Int,
    var gloss: String? = null,
    var fullGloss: String? = null,
    source: List<SourceRef> = emptyList(),
    notes: String? = null
) : LangEntity(id, source, notes) {
    var components: List<Word> = components.toList()
        private set
    var headIndex: Int = headIndex
        private set

    init {
        validateComponents(components, headIndex)
    }

    fun headComponent(): Word = components[headIndex]

    fun updateComponents(components: List<Word>, headIndex: Int) {
        validateComponents(components, headIndex)
        this.components = components.toList()
        this.headIndex = headIndex
    }

    private fun validateComponents(components: List<Word>, headIndex: Int) {
        require(headIndex in components.indices) { "An open compound must have a head component" }
        require(components.distinctBy { it.id }.size == components.size) {
            "An open compound cannot contain the same word more than once"
        }
    }
}
