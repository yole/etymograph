package ru.yole.etymograph

data class RichTextFragment(
    val text: String,
    val tooltip: String? = null,
    val emph: Boolean = false,
    val subscript: Boolean = false,
    val linkType: String? = null,
    val linkId: Int? = null,
    val linkLanguage: String? = null,
    val linkData: String? = null
) {
    override fun toString(): String = text

    operator fun plus(fragment: RichTextFragment): RichText {
        if (text.isEmpty()) {
            return RichText(listOf(fragment))
        }
        if (fragment.text.isEmpty()) {
            return RichText(listOf(this))
        }
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

    operator fun plus(text: RichText): RichText {
        return RichText(fragments + text.fragments)
    }

    operator fun plus(s: String): RichText {
        if (s.isEmpty()) {
            return this
        }
        return RichText(fragments + s.rich())
    }
}

fun String.rich(
    emph: Boolean = false, subscript: Boolean = false, tooltip: String? = null,
    linkType: String? = null, linkId: Int? = null, linkLanguage: String? = null, linkData: String? = null
): RichTextFragment =
    RichTextFragment(this, tooltip, emph, subscript, linkType, linkId, linkLanguage, linkData)

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
