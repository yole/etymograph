package ru.yole.etymograph

import javax.swing.RowFilter

data class RichTextFragment(val text: String, val emph: Boolean = false, val linkType: String? = null, val linkId: Int? = null) {
    override fun toString(): String = text

    operator fun plus(fragment: RichTextFragment): RichText {
        return RichText(listOf(this, fragment))
    }
}

data class RichText(val fragments: List<RichTextFragment>) {
    override fun toString(): String = fragments.joinToString("")

    fun prepend(s: String): RichText {
        return RichText(s.richText().fragments + fragments)
    }

    fun append(s: String): RichText {
        return RichText(fragments + s.richText().fragments)
    }

    operator fun plus(fragment: RichTextFragment): RichText {
        if (fragment.text.isEmpty()) {
            return this
        }
        return RichText(fragments + fragment)
    }

    operator fun plus(s: String): RichText {
        if (s.isEmpty()) {
            return this
        }
        return RichText(fragments + s.rich())
    }
}

fun String.rich(emph: Boolean = false, linkType: String? = null, linkId: Int? = null): RichTextFragment =
    RichTextFragment(this, emph, linkType, linkId)

fun String.richText(): RichText = richText(RichTextFragment(this))

fun richText(vararg fragments: RichTextFragment): RichText =
    RichText(fragments.toList())

fun <T> List<T>.joinToRichText(separator: String, callback: (T) -> RichText): RichText {
    val fragments = mutableListOf<RichTextFragment>()
    for ((index, element) in this.withIndex()) {
        if (index > 0) fragments.add(separator.rich())
        fragments.addAll(callback(element).fragments)
    }
    return RichText(fragments)
}
