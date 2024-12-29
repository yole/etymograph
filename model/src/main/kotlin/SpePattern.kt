package ru.yole.etymograph

import kotlin.math.min

sealed class SpeNode {
    abstract fun match(it: PhonemeIterator): Boolean
    abstract fun matchBackwards(it: PhonemeIterator): Boolean
    abstract fun toRichText(language: Language?): RichText
    open fun buildReplacementTooltip(beforeNode: SpeNode?, language: Language?): RichText {
        return toRichText(language)
    }
    open fun refersToPhoneme(phoneme: Phoneme) = false

    override fun toString(): String {
        return toRichText(null).toString()
    }
}

sealed class SpeTargetNode : SpeNode() {
    abstract fun replace(it: PhonemeIterator, i: Int, trace: RuleTrace?)
    abstract fun insert(it: PhonemeIterator, i: Int)
}

abstract class SpePhonemeNode : SpeTargetNode() {
    override fun match(it: PhonemeIterator): Boolean {
        if (matchCurrent(it)) {
            it.advance()
            return true
        }
        return false
    }

    override fun matchBackwards(it: PhonemeIterator): Boolean {
        if (!it.advanceBy(-1)) {
            return false
        }
        return matchCurrent(it)
    }

    abstract fun matchCurrent(it: PhonemeIterator): Boolean
}

class SpeLiteralNode(val text: String) : SpePhonemeNode() {
    override fun toRichText(language: Language?): RichText {
        val phoneme = language?.phonemes?.find { text == it.effectiveSound }
        if (phoneme != null) {
            return richText(text.rich(linkType = "phoneme", linkId = phoneme.id))
        }
        return text.richText()
    }

    override fun matchCurrent(it: PhonemeIterator): Boolean {
        return text == it.current
    }

    override fun replace(it: PhonemeIterator, i: Int, trace: RuleTrace?) {
        it.replaceAtRelative(i, text)
    }

    override fun insert(it: PhonemeIterator, i: Int) {
        it.insertAtRelative(i, text)
    }

    override fun refersToPhoneme(phoneme: Phoneme): Boolean {
        return text == phoneme.effectiveSound
    }
}

class SpePhonemeClassNode(val language: Language, val phonemeClass: PhonemeClass): SpePhonemeNode() {
    override fun matchCurrent(it: PhonemeIterator): Boolean {
        return phonemeClass.matchesCurrent(it)
    }

    override fun toRichText(language: Language?): RichText {
        val matchingPhonemes = phonemeClass.matchingPhonemes.takeIf { it.isNotEmpty() }?.joinToString(", ")
        if (phonemeClass.name == PhonemeClass.consonantClassName) {
            return richText("C".rich(tooltip = matchingPhonemes))
        }
        if (phonemeClass.name == PhonemeClass.vowelClassName) {
            return richText("V".rich(tooltip = matchingPhonemes))
        }
        val name = if (phonemeClass is NegatedPhonemeClass)
            "-" + phonemeClass.baseClass.name.trimStart('+')
        else if (!phonemeClass.name.startsWith("+") &&
            language?.phonemeClassByName("-" + phonemeClass.name) != null)
        {
            "+" + phonemeClass.name
        }
        else
            phonemeClass.name

        return "[".rich() + name.rich(tooltip = matchingPhonemes)+ "]".rich()
    }

    override fun replace(it: PhonemeIterator, i: Int, trace: RuleTrace?) {
        replacePhonemeByFeatures(it, i, phonemeClass, trace)
    }

    override fun insert(it: PhonemeIterator, i: Int) {
        throw UnsupportedOperationException()
    }

    private fun replacePhonemeByFeatures(it: PhonemeIterator, relativeIndex: Int, newClass: PhonemeClass, trace: RuleTrace? = null) {
        val phonemeText = it.atRelative(relativeIndex)
        val phoneme = language.phonemes.find { phonemeText in it.graphemes }
        if (phoneme == null) {
            trace?.logInstruction { "Not found matching phoneme" }
            return
        }
        val newPhoneme = findReplacementPhoneme(it.language, phoneme, newClass).singleOrNull()
        newPhoneme?.let { p ->
            trace?.logInstruction { "Replacing phoneme with ${p.effectiveSound}" }
            it.replaceAtRelative(relativeIndex, p.effectiveSound)
        } ?: run {
            trace?.logInstruction { "No replacement phoneme for ${phoneme.effectiveSound} with ${newClass.name}" }
        }
    }

    override fun buildReplacementTooltip(beforeNode: SpeNode?, language: Language?): RichText {
        val beforeClass = (beforeNode as? SpePhonemeClassNode)?.phonemeClass
        if (beforeClass != null) {
            return buildReplacementTooltip(beforeClass, phonemeClass)
        }
        return super.buildReplacementTooltip(beforeNode, language)
    }

    private fun buildReplacementTooltip(beforeClass: PhonemeClass, afterClass: PhonemeClass): RichText {
        val tooltip = language.phonemes.mapNotNull { p ->
            if (p.effectiveSound in beforeClass.matchingPhonemes) {
                val replacement = findReplacementPhoneme(language, p, afterClass)
                val replacementText = replacement.singleOrNull()?.effectiveSound
                    ?: (replacement.takeIf { it.isNotEmpty() }?.joinToString { it.effectiveSound }?.plus("?"))
                    ?: "?"
                "${p.effectiveSound} -> $replacementText"
            }
            else {
                null
            }
        }.joinToString(", ")

        val name = if (afterClass is NegatedPhonemeClass)
            "-" + afterClass.baseClass.name.trimStart('+')
        else
            afterClass.name
        return "[".rich() + name.rich(tooltip = tooltip)+ "]".rich()
    }

    private fun findReplacementPhoneme(
        fromLanguage: Language,
        phoneme: Phoneme,
        newClass: PhonemeClass
    ): List<Phoneme> {
        val features = fromLanguage.phonemeFeatures(phoneme).toMutableSet()
        val newClasses = (newClass as? IntersectionPhonemeClass)?.classList ?: listOf(newClass)
        for (newPhonemeClass in newClasses) {
            if (newPhonemeClass is NegatedPhonemeClass) {
                features.remove(newPhonemeClass.baseClass.name)
            }
            else {
                features.removeAll(fromLanguage.contradictingFeatures(newPhonemeClass.name))
                features.add(newPhonemeClass.name)
            }
        }
        return language.phonemes.filter { p ->
            language.phonemeFeatures(p).containsAll(features)
        }
    }
}

object SpeWordBoundaryNode : SpeNode() {
    override fun match(it: PhonemeIterator): Boolean {
        return it.atEnd()
    }

    override fun matchBackwards(it: PhonemeIterator): Boolean {
        return it.atBeginning()
    }

    override fun toRichText(language: Language?): RichText {
        return "#".richText()
    }
}

class SpePattern(
    private val fromLanguage: Language,
    private val toLanguage: Language,
    val before: List<SpeNode>,
    val after: List<SpeTargetNode>,
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
                for (i in 0..<min(beforeLength, afterLength)) {
                    after[i].replace(it, i, trace)
                }
                if (beforeLength < afterLength) {
                    for (i in beforeLength..<afterLength) {
                        after[i].insert(it, i)
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
            result += node.buildReplacementTooltip(before.singleOrNull(), toLanguage)
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
                    parseTargetNodes(toLanguage, afterTextWithContext),
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
                parseTargetNodes(toLanguage, afterText),
                parseNodes(fromLanguage, precedeText),
                parseNodes(fromLanguage, followText)
            )
        }

        private fun parseTargetNodes(language: Language, text: String): List<SpeTargetNode> {
            val nodes = parseNodes(language, text)
            nodes.find { it !is SpeTargetNode}?.let {
                throw IllegalArgumentException("Node $it cannot be used in 'after' position")
            }
            @Suppress("UNCHECKED_CAST")
            return nodes as List<SpeTargetNode>
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
                    result.add(SpePhonemeClassNode(language, parseClass(language, classText)))
                    pos = classEnd + 1
                }
                else {
                    val nextPhoneme = language.phonoPhonemeLookup.nextPhoneme(text, pos)
                    result.add(when(nextPhoneme) {
                        "#"-> SpeWordBoundaryNode
                        "C" -> SpePhonemeClassNode(language,
                            language.phonemeClassByName(PhonemeClass.consonantClassName) ?: throw SpeParseException("Consonant class not found"))
                        "V" -> SpePhonemeClassNode(language,
                            language.phonemeClassByName(PhonemeClass.vowelClassName) ?: throw SpeParseException("Vowel class not found"))
                        else -> SpeLiteralNode(nextPhoneme)
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
                language.phonemeClassByName("+" + text.substring(1))?.let {
                    return NegatedPhonemeClass(it)
                }
            }
            if (text.startsWith("+")) {
                val baseName = text.substring(1)
                val baseClass = language.phonemeClassByName(baseName)
                if (baseClass != null && language.phonemeClassByName("-$baseName") != null) {
                    return baseClass
                }
            }

            throw SpeParseException("Can't find phoneme class $text")
        }
    }
}

class SpeParseException(error: String) : IllegalArgumentException(error)
