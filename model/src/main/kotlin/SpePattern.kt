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

    override fun toString(): String {
        if (wordBoundary) {
            return "#"
        }
        if (phonemeClass != null) {
            return "[" + phonemeClass.name + "]"
        }
        return text ?: ""
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
                it.replace(after.single().text!!)
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

    override fun toString(): String {
        val beforeAfter = before.joinToString("") + " -> " + after.joinToString("")
        if (preceding.isEmpty() && following.isEmpty()) {
            return beforeAfter
        }
        return beforeAfter + " / " + preceding.joinToString("") + "_" + following.joinToString("")
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
                else if (text[pos] == '#') {
                    result.add(SpeNode(null, true, null))
                    pos++
                }
                else {
                    result.add(SpeNode(text.substring(pos, 1), false, null))
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
