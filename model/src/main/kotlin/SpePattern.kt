package ru.yole.etymograph

class SpeNode(val text: String?, val wordBoundary: Boolean, val phonemeClass: PhonemeClass?) {
    fun match(it: PhonemeIterator): Boolean {
        if (wordBoundary) {
            return it.atEnd()
        }
        if (matchCurrent(it)) {
            it.advance()
            return true
        }
        return false
    }

    fun matchBackwards(it: PhonemeIterator): Boolean {
        if (wordBoundary) {
            return it.atBeginning()
        }
        if (!it.advanceBy(-1)) {
            return false
        }
        return matchCurrent(it)
    }

    private fun matchCurrent(it: PhonemeIterator): Boolean {
        if (phonemeClass != null) {
            return phonemeClass.matchesCurrent(it)
        }
        return text == it.current
    }

    fun toRichText(): RichText {
        if (wordBoundary) {
            return "#".richText()
        }
        if (phonemeClass != null) {
            val matchingPhonemes = phonemeClass.matchingPhonemes.takeIf { it.isNotEmpty() }?.joinToString(", ")
            if (phonemeClass.name == PhonemeClass.consonantClassName) {
                return richText("C".rich(tooltip = matchingPhonemes))
            }
            if (phonemeClass.name == PhonemeClass.vowelClassName) {
                return richText("V".rich(tooltip = matchingPhonemes))
            }
            return "[".rich() + phonemeClass.name.rich(tooltip = matchingPhonemes)+ "]".rich()
        }
        return (text ?: "").richText()
    }
}

class SpePattern(
    val before: List<SpeNode>,
    val after: List<SpeNode>,
    val preceding: List<SpeNode>,
    val following: List<SpeNode>
)  {
    fun apply(language: Language, text: String): String {
        val it = PhonemeIterator(text, language, null)
        while (true) {
            val itCopy = it.clone()
            if (matchNodes(itCopy, before) && matchNodes(itCopy, following) && matchNodesBackwards(it.clone(), preceding)) {
                val beforeLength = before.size
                val afterLength = after.size
                for (i in 0..<Math.min(beforeLength, afterLength)) {
                    it.replaceAtRelative(i, after[i].text!!)
                }
                if (beforeLength < afterLength) {
                    for (i in beforeLength..<afterLength) {
                        it.insertAtRelative(i, after[i].text!!)
                    }
                }
                if (beforeLength > afterLength) {
                    for (i in afterLength..<beforeLength) {
                        it.deleteAtRelative(i)
                    }
                }
            }
            if (!it.advance()) break
        }

        return it.result()
    }

    private fun matchNodes(it: PhonemeIterator, nodes: List<SpeNode>): Boolean {
        return nodes.all { node -> node.match(it) }
    }

    private fun matchNodesBackwards(it: PhonemeIterator, nodes: List<SpeNode>): Boolean {
        return nodes.reversed().all { node -> node.matchBackwards(it) }
    }

    fun toRichText(): RichText {
        var result = RichText(emptyList())
        for (node in before) {
            result += node.toRichText()
        }
        if (before.isEmpty()) {
            result += "∅".rich()
        }
        result += " → ".rich()
        for (node in after) {
            result += node.toRichText()
        }
        if (after.isEmpty()) {
            result += "∅".rich()
        }
        if (preceding.isEmpty() && following.isEmpty()) {
            return result
        }
        result += " / ".rich()
        for (node in preceding) {
            result += node.toRichText()
        }
        result += "_".rich()
        for (node in following) {
            result += node.toRichText()
       }
        return result
    }

    override fun toString(): String {
        return toRichText().toString()
            .replace("→", "->")
            .replace('∅', '0')
    }

    companion object {
        fun parse(language: Language, text: String): SpePattern {
            val arrow = text.indexOf("->")
            if (arrow < 0) {
                throw SpeParseException("-> required in SPE pattern")
            }
            val beforeText = text.substring(0, arrow).trim()
            val afterTextWithContext = text.substring(arrow + 2).trim()
            val slash = afterTextWithContext.indexOf('/')
            if (slash < 0) {
                return SpePattern(
                    parseNodes(language, beforeText),
                    parseNodes(language, afterTextWithContext),
                    emptyList(),
                    emptyList()
                )
            }
            val afterText = afterTextWithContext.substring(0, slash).trim()
            val context = afterTextWithContext.substring(slash + 1).trim()
            val underscore = context.indexOf('_')
            if (underscore < 0) {
                throw SpeParseException("_ required in SPE pattern context")
            }
            val precedeText = context.substring(0, underscore).trim()
            val followText = context.substring(underscore + 1).trim()
            return SpePattern(
                parseNodes(language, beforeText),
                parseNodes(language, afterText),
                parseNodes(language, precedeText),
                parseNodes(language, followText)
            )
        }

        private fun parseNodes(language: Language, text: String): List<SpeNode> {
            if (text == "0") {
                return emptyList()
            }
            val result = mutableListOf<SpeNode>()
            var pos = 0
            while (pos < text.length) {
                if (text[pos] == '[') {
                    val classEnd = text.indexOf(']', pos)
                    if (classEnd < 0) {
                        throw SpeParseException("Missing closing bracket in character class")
                    }
                    val classText = text.substring(pos + 1, classEnd)
                    result.add(SpeNode(null, false, parseClass(language, classText)))
                    pos = classEnd + 1
                }
                else {
                    result.add(when(text[pos]) {
                        '#' -> SpeNode(null, true, null)
                        'C' -> SpeNode(null, false,
                            language.phonemeClassByName(PhonemeClass.consonantClassName) ?: throw SpeParseException("Consonant class not found"))
                        'V' -> SpeNode(null, false,
                            language.phonemeClassByName(PhonemeClass.vowelClassName) ?: throw SpeParseException("Vowel class not found"))
                        else -> SpeNode(text.substring(pos, pos + 1), false, null)
                    })
                    pos++
                }
            }
            return result
        }

        private fun parseClass(language: Language, text: String): PhonemeClass {
            return language.phonemeClassByName(text)
                ?: throw SpeParseException("Can't find phoneme class $text")
        }
    }
}

class SpeParseException(error: String) : IllegalArgumentException(error)
