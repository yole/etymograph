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

    fun toRichText(language: Language?): RichText {
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
            val name = if (phonemeClass is NegatedPhonemeClass) "-" + phonemeClass.baseClass.name else phonemeClass.name
            return "[".rich() + name.rich(tooltip = matchingPhonemes)+ "]".rich()
        }
        if (text != null) {
            val phoneme = language?.phonemes?.find { text in it.graphemes }
            if (phoneme != null) {
                return richText(text.rich(linkType = "phoneme", linkId = phoneme.id))
            }
            return text.richText()
        }
        return "".richText()
    }

    override fun toString(): String {
        return toRichText(null).toString()
    }
}

class SpePattern(
    private val fromLanguage: Language,
    private val toLanguage: Language,
    val before: List<SpeNode>,
    val after: List<SpeNode>,
    val preceding: List<SpeNode>,
    val following: List<SpeNode>
)  {
    fun apply(language: Language, text: String, trace: RuleTrace? = null): String {
        return apply(Word(-1, text, language), trace)
    }

    fun apply(word: Word, trace: RuleTrace? = null): String {
        val it = PhonemeIterator(word, null)
        while (true) {
            val itCopy = it.clone()
            if (matchNodes(itCopy, before, trace) &&
                matchNodes(itCopy, following, trace) &&
                matchNodesBackwards(it.clone(), preceding, trace))
            {
                val beforeLength = before.size
                val afterLength = after.size
                for (i in 0..<Math.min(beforeLength, afterLength)) {
                    val afterClass = after[i].phonemeClass
                    if (afterClass != null) {
                        replacePhonemeByFeatures(it, i, afterClass)
                    }
                    else {
                        it.replaceAtRelative(i, after[i].text!!)
                    }
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

    private fun replacePhonemeByFeatures(it: PhonemeIterator, relativeIndex: Int, newClass: PhonemeClass) {
        val phonemeText = it.atRelative(relativeIndex)
        val phoneme = it.language.phonemes.find { phonemeText in it.graphemes }
        if (phoneme == null) {
            return
        }
        val newPhoneme = findReplacementPhoneme(it.language, phoneme, newClass)
        newPhoneme?.let { p ->
            it.replaceAtRelative(relativeIndex, p.graphemes[0])
        }
    }

    private fun findReplacementPhoneme(
        fromLanguage: Language,
        phoneme: Phoneme,
        newClass: PhonemeClass
    ): Phoneme? {
        val features = fromLanguage.phonemeFeatures(phoneme).toMutableSet()
        val newClasses = (newClass as? IntersectionPhonemeClass)?.classList ?: listOf(newClass)
        for (newPhonemeClass in newClasses) {
            features.removeAll(fromLanguage.contradictingFeatures(newPhonemeClass.name))
            features.add(newPhonemeClass.name)
        }
        val newPhoneme = toLanguage.phonemes.filter { p ->
            toLanguage.phonemeFeatures(p) == features
        }
        return newPhoneme.singleOrNull()
    }

    private fun matchNodes(it: PhonemeIterator, nodes: List<SpeNode>, trace: RuleTrace? = null): Boolean {
        return nodes.all {
            node -> node.match(it).also { result -> trace?.logNodeMatch(it, node, result) }
        }
    }

    private fun matchNodesBackwards(it: PhonemeIterator, nodes: List<SpeNode>, trace: RuleTrace? = null): Boolean {
        return nodes.reversed().all {
            node -> node.matchBackwards(it).also { result -> trace?.logNodeMatch(it, node, result) }
        }
    }

    fun toRichText(): RichText {
        var result = RichText(emptyList())
        for (node in before) {
            result += node.toRichText(fromLanguage)
        }
        if (before.isEmpty()) {
            result += "∅".rich()
        }
        result += " → ".rich()
        for (node in after) {
            val beforeClass = before.singleOrNull()?.phonemeClass
            if (node.phonemeClass != null && beforeClass != null) {
                result += buildReplacementTooltip(beforeClass, node.phonemeClass)
            }
            else {
                result += node.toRichText(toLanguage)
            }
        }
        if (after.isEmpty()) {
            result += "∅".rich()
        }
        if (preceding.isEmpty() && following.isEmpty()) {
            return result
        }
        result += " / ".rich()
        for (node in preceding) {
            result += node.toRichText(fromLanguage)
        }
        result += "_".rich()
        for (node in following) {
            result += node.toRichText(fromLanguage)
       }
        return result
    }

    private fun buildReplacementTooltip(beforeClass: PhonemeClass, afterClass: PhonemeClass): RichText {
        val tooltip = fromLanguage.phonemes.mapNotNull { p ->
            if (p.effectiveSound in beforeClass.matchingPhonemes) {
                val replacement = findReplacementPhoneme(fromLanguage, p, afterClass)
                "${p.effectiveSound} -> ${replacement?.effectiveSound ?: "?"}"
            }
            else {
                null
            }
        }.joinToString(", ")

        val name = if (afterClass is NegatedPhonemeClass) "-" + afterClass.baseClass.name else afterClass.name
        return "[".rich() + name.rich(tooltip = tooltip)+ "]".rich()
    }

    override fun toString(): String {
        return toRichText().toString()
            .replace("→", "->")
            .replace('∅', '0')
    }

    companion object {
        fun parse(fromLanguage: Language, toLanguage: Language, text: String): SpePattern {
            val arrow = text.indexOf("->")
            if (arrow < 0) {
                throw SpeParseException("-> required in SPE pattern")
            }
            val beforeText = text.substring(0, arrow).trim()
            val afterTextWithContext = text.substring(arrow + 2).trim()
            val slash = afterTextWithContext.indexOf('/')
            if (slash < 0) {
                return SpePattern(
                    fromLanguage,
                    toLanguage,
                    parseNodes(fromLanguage, beforeText),
                    parseNodes(toLanguage, afterTextWithContext),
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
                fromLanguage,
                toLanguage,
                parseNodes(fromLanguage, beforeText),
                parseNodes(toLanguage, afterText),
                parseNodes(fromLanguage, precedeText),
                parseNodes(fromLanguage, followText)
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
                    val nextPhoneme = language.phonoPhonemeLookup.nextPhoneme(text, pos)
                    result.add(when(nextPhoneme) {
                        "#"-> SpeNode(null, true, null)
                        "C" -> SpeNode(null, false,
                            language.phonemeClassByName(PhonemeClass.consonantClassName) ?: throw SpeParseException("Consonant class not found"))
                        "V" -> SpeNode(null, false,
                            language.phonemeClassByName(PhonemeClass.vowelClassName) ?: throw SpeParseException("Vowel class not found"))
                        else -> SpeNode(nextPhoneme, false, null)
                    })
                    pos += nextPhoneme.length
                }
            }
            return result
        }

        private fun parseClass(language: Language, text: String): PhonemeClass {
            val items = text.split(',')
            if (items.size > 1) {
                val subclasses = items.map { parseSingleClass(language, it.trim()) }
                return IntersectionPhonemeClass(text, subclasses)
            }

            return parseSingleClass(language, text)
        }

        private fun parseSingleClass(language: Language, text: String): PhonemeClass {
            language.phonemeClassByName(text)?.let { return it }
            if (text.startsWith("-")) {
                language.phonemeClassByName(text.substring(1))?.let {
                    return NegatedPhonemeClass(it)
                }
            }

            throw SpeParseException("Can't find phoneme class $text")
        }
    }
}

class SpeParseException(error: String) : IllegalArgumentException(error)
