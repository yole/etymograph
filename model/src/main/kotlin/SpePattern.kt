package ru.yole.etymograph

import kotlin.math.min

class SpeContext(val trace: RuleTrace?, private val pattern: SpePattern) {
    private val matchIndexes = mutableMapOf<Int, Int>()
    private val matchedClasses = mutableMapOf<String, String>()

    fun recordMatchedClass(node: SpeNode, current: String) {
        matchedClasses[node.toString()] = current
    }

    fun findMatchForClass(node: SpeNode): String? {
        return matchedClasses[node.toString()]
    }

    fun recordMatchedAlternative(node: SpeAlternativeNode, index: Int) {
        val nodeIndex = pattern.before.indexOf(node)
        if (nodeIndex >= 0) {
            matchIndexes[nodeIndex] = index
        }
    }

    fun findMatchedAlternative(node: SpeNode): Int? {
        val nodeIndex = pattern.after.indexOf(node)
        return if (nodeIndex < 0) null else matchIndexes[nodeIndex]
    }

    fun getBeforeNode(node: SpeNode): SpeAlternativeNode? {
        val nodeIndex = pattern.after.indexOf(node)
        return if (nodeIndex < 0) null else pattern.before[nodeIndex] as? SpeAlternativeNode
    }
}

sealed class SpeNode {
    abstract fun match(it: PhonemeIterator, context: SpeContext): Boolean
    abstract fun matchBackwards(it: PhonemeIterator, context: SpeContext): Boolean
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
    abstract fun replace(it: PhonemeIterator, relativeIndex: Int, context: SpeContext)
    abstract fun insert(it: PhonemeIterator, relativeIndex: Int, context: SpeContext)
}

abstract class SpePhonemeNode : SpeTargetNode() {
    override fun match(it: PhonemeIterator, context: SpeContext): Boolean {
        if (it.atEnd()) {
            return false
        }
        if (matchCurrent(it, context)) {
            it.advance()
            return true
        }
        return false
    }

    override fun matchBackwards(it: PhonemeIterator, context: SpeContext): Boolean {
        if (!it.advanceBy(-1)) {
            return false
        }
        return matchCurrent(it, context)
    }

    abstract fun matchCurrent(it: PhonemeIterator, context: SpeContext): Boolean
}

class SpeLiteralNode(val text: String) : SpePhonemeNode() {
    override fun toRichText(language: Language?): RichText {
        return text.richText()
    }

    override fun matchCurrent(it: PhonemeIterator, context: SpeContext): Boolean {
        return text == it.current
    }

    override fun replace(it: PhonemeIterator, relativeIndex: Int, context: SpeContext) {
        val index = context.findMatchedAlternative(this)
        val beforeNode = context.getBeforeNode(this)
        if (index != null && beforeNode != null) {
            // create copy of this node to make sure we don't find the before node again when doing the alternative replacement
            applyNodes(it, relativeIndex, context, beforeNode.choices[index], listOf(SpeLiteralNode(text)))
        }
        else {
            it.replaceAtRelative(relativeIndex, text)
        }
    }

    override fun insert(it: PhonemeIterator, relativeIndex: Int, context: SpeContext) {
        it.insertAtRelative(relativeIndex, text)
    }

    override fun refersToPhoneme(phoneme: Phoneme): Boolean {
        return text == phoneme.effectiveSound
    }
}

class SpePhonemeClassNode(val language: Language, val phonemeClass: PhonemeClass): SpePhonemeNode() {
    override fun matchCurrent(it: PhonemeIterator, context: SpeContext): Boolean {
        return phonemeClass.matchesCurrent(it).also { result ->
            if (result && phonemeClass.isShorthand()) context.recordMatchedClass(this, it.current)
        }
    }

    override fun toRichText(language: Language?): RichText {
        val matchingPhonemes = phonemeClass.matchingPhonemes.takeIf { it.isNotEmpty() }?.joinToString(", ")
        if (phonemeClass.isShorthand()) {
            return richText(phonemeClass.name.rich(tooltip = matchingPhonemes))
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

    override fun replace(it: PhonemeIterator, relativeIndex: Int, context: SpeContext) {
        if (phonemeClass.isShorthand()) {
            val match = context.findMatchForClass(this) ?: return
            it.replaceAtRelative(relativeIndex, match)
        }
        replacePhonemeByFeatures(it, relativeIndex, phonemeClass, context.trace)
    }

    override fun insert(it: PhonemeIterator, relativeIndex: Int, context: SpeContext) {
        val match = context.findMatchForClass(this) ?: return
        it.insertAtRelative(relativeIndex, match)
    }

    private fun replacePhonemeByFeatures(it: PhonemeIterator, relativeIndex: Int, newClass: PhonemeClass, trace: RuleTrace? = null) {
        val phonemeText = it.atRelative(relativeIndex)
        val phoneme = language.phonemes.find { phonemeText == it.effectiveSound }
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
                if (p.effectiveSound == replacementText) {
                    null
                }
                else {
                    "${p.effectiveSound} > $replacementText"
                }
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
        val matching = language.phonemes.filter { p ->
            language.phonemeFeatures(p).containsAll(features)
        }
        if (matching.size == 1) {
            return matching
        }
        return language.phonemes.filter { p ->
            language.phonemeFeatures(p) == features
        }
    }
}

object SpeWordBoundaryNode : SpeNode() {
    override fun match(it: PhonemeIterator, context: SpeContext): Boolean {
        return it.atEnd()
    }

    override fun matchBackwards(it: PhonemeIterator, context: SpeContext): Boolean {
        return it.atBeginning()
    }

    override fun toRichText(language: Language?): RichText {
        return "#".richText()
    }
}

class SpeAlternativeNode(val choices: List<List<SpeNode>>) : SpeTargetNode() {
    override fun match(it: PhonemeIterator, context: SpeContext): Boolean {
        for ((index, choice) in choices.withIndex()) {
            val copy = it.clone()
            if (choice.matchNodes(copy, context)) {
                context.recordMatchedAlternative(this, index)
                it.catchUp(copy)
                return true
            }
        }
        return false
    }

    override fun matchBackwards(it: PhonemeIterator, context: SpeContext): Boolean {
        for (choice in choices) {
            val copy = it.clone()
            if (choice.matchNodesBackwards(copy, context)) {
                it.catchUp(copy)
                return true
            }
        }
        return false
    }

    override fun toRichText(language: Language?): RichText {
        var result = "{".richText()
        for ((index, choice) in choices.withIndex()) {
            if (index > 0) {
                result += "|"
            }
            if (choice.isEmpty()) {
                result += "0"
            }
            else {
                result += choice.joinToRichText("") {
                    it.toRichText(language)
                }
            }
        }
        result += "}"
        return result
    }

    override fun replace(it: PhonemeIterator, relativeIndex: Int, context: SpeContext) {
        val index = context.findMatchedAlternative(this)
        val beforeNode = context.getBeforeNode(this)
        if (index != null && beforeNode != null) {
            applyNodes(it, relativeIndex, context, beforeNode.choices[index], choices[index] as List<SpeTargetNode>)
        }
    }

    override fun insert(it: PhonemeIterator, relativeIndex: Int, context: SpeContext) {
        throw UnsupportedOperationException()
    }
}

class SpeRepeatNode(private val base: SpeNode): SpeNode() {
    override fun match(it: PhonemeIterator, context: SpeContext): Boolean {
        while (base.match(it, context));
        return true
    }

    override fun matchBackwards(it: PhonemeIterator, context: SpeContext): Boolean {
        while (base.matchBackwards(it, context));
        return true
    }

    override fun toRichText(language: Language?): RichText {
        return base.toRichText(language) + "0".rich(subscript = true)
    }
}

class SpeOptionalNode(private val base: List<SpeNode>): SpeNode() {
    override fun match(it: PhonemeIterator, context: SpeContext): Boolean {
        val copy = it.clone()
        if (base.matchNodes(copy, context)) {
            it.catchUp(copy)
        }
        return true
    }

    override fun matchBackwards(it: PhonemeIterator, context: SpeContext): Boolean {
        val copy = it.clone()
        if (base.matchNodesBackwards(copy, context)) {
            it.catchUp(copy)
        }
        return true
    }

    override fun toRichText(language: Language?): RichText {
        return "(".richText() + base.joinToRichText("") { it.toRichText(language) } + ")"
    }
}

class SpeNegateNode(private val baseNode: SpeNode): SpeNode() {
    override fun match(it: PhonemeIterator, context: SpeContext): Boolean {
        return !baseNode.match(it, context)
    }

    override fun matchBackwards(it: PhonemeIterator, context: SpeContext): Boolean {
        return !baseNode.matchBackwards(it, context)
    }

    override fun toRichText(language: Language?): RichText {
        return "!".richText() + baseNode.toRichText(language)
    }
}

private fun List<SpeNode>.matchNodes(it: PhonemeIterator, context: SpeContext): Boolean {
    return all { node ->
        if (context.trace != null) {
            val itCopy = it.clone()
            node.match(it, context).also { result -> context.trace.logNodeMatch(itCopy, node, result) }
        } else {
            node.match(it, context)
        }
    }
}

private fun List<SpeNode>.matchNodesBackwards(it: PhonemeIterator, context: SpeContext): Boolean {
    return reversed().all { node ->
        if (context.trace != null) {
            val itCopy = it.clone()
            node.matchBackwards(it, context).also { result -> context.trace.logNodeMatch(itCopy, node, result) }
        }
        else {
            node.matchBackwards(it, context)
        }
    }
}

private fun applyNodes(it: PhonemeIterator, relativeIndex: Int, context: SpeContext, before: List<SpeNode>, after: List<SpeTargetNode>) {
    val beforeLength = before.size
    val afterLength = after.size
    for (i in 0..<min(beforeLength, afterLength)) {
        after[i].replace(it, i + relativeIndex, context)
    }
    if (beforeLength < afterLength) {
        for (i in beforeLength..<afterLength) {
            after[i].insert(it, i + relativeIndex, context)
        }
    }
    if (beforeLength > afterLength) {
        for (i in afterLength..<beforeLength) {
            it.deleteAtRelative(i + relativeIndex)
        }
    }
    if (beforeLength > 1) {
        it.advanceBy(beforeLength - 1)
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
        val it = PhonemeIterator(Word(-1, text, language), null)
        apply(it, null, null, trace = trace)
        return it.result()
    }

    fun apply(
        it: PhonemeIterator,
        condition: ((PhonemeIterator) -> Boolean)? = null,
        postApply: ((PhonemeIterator) -> Unit)?,
        trace: RuleTrace? = null
    ): Boolean {
        var anyChanges = false
        while (true) {
            anyChanges = anyChanges or applyAtCurrent(it, condition, postApply, trace)
            if (!it.advance()) break
        }
        return anyChanges
    }

    fun applyAtCurrent(
        it: PhonemeIterator,
        condition: ((PhonemeIterator) -> Boolean)?,
        postApply: ((PhonemeIterator) -> Unit)?,
        trace: RuleTrace?
    ): Boolean {
        val itCopy = it.clone()
        val context = SpeContext(trace, this)
        if (before.matchNodes(itCopy, context) &&
            following.matchNodes(itCopy, context) &&
            preceding.matchNodesBackwards(it.clone(), context) &&
            (condition == null || condition.invoke(it.clone()))
        ) {
            applyNodes(it, 0, context, before, after)
            postApply?.invoke(it)
            return true
        }
        return false
    }

    fun toRichText(): RichText {
        var result = RichText(emptyList())
        for (node in before) {
            result += node.toRichText(fromLanguage)
        }
        if (before.isEmpty()) {
            result += "∅".rich()
        }
        result += " > ".rich()
        for ((index, node) in after.withIndex()) {
            val beforeNode = before.getOrNull(index)
            if (beforeNode != null && beforeNode.toString() == node.toString()) {
                result += node.toRichText(fromLanguage)
            }
            else {
                result += node.buildReplacementTooltip(beforeNode, toLanguage)
            }
        }
        if (after.isEmpty()) {
            result += "∅".rich()
        }
        if (preceding.isEmpty() && following.isEmpty()) {
            return result.format(monospaced = true)
        }
        result += " / ".rich()
        for (node in preceding) {
            result += node.toRichText(fromLanguage)
        }
        result += "_".rich()
        for (node in following) {
            result += node.toRichText(fromLanguage)
       }
        return result.format(monospaced = true)
    }

    override fun toString(): String {
        return toRichText().toString()
            .replace('∅', '0')
    }

    companion object {
        fun parse(fromLanguage: Language, toLanguage: Language, text: String): SpePattern {
            val text = text.replace("->", ">")
            val arrow = text.indexOf(">")
            if (arrow < 0) {
                throw SpeParseException("> required in SPE pattern")
            }
            val beforeText = text.substring(0, arrow).trim()
            val afterTextWithContext = text.substring(arrow + 1).trim()
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
                val (nextPos, node) = parseNextNode(text, pos, language)
                if (node is SpeRepeatNode) {
                    result[result.size - 1] = SpeRepeatNode(result.last())
                }
                else {
                    result.add(node)
                }
                pos = nextPos
            }
            return result
        }

        private fun parseNextNode(text: String, pos: Int, language: Language): Pair<Int, SpeNode> {
            if (text[pos] == '[') {
                val classEnd = text.indexOf(']', pos)
                if (classEnd < 0) {
                    throw SpeParseException("Missing closing bracket in character class")
                }
                val classText = text.substring(pos + 1, classEnd)
                return classEnd + 1 to SpePhonemeClassNode(language, parseClass(language, classText))
            }
            else if (text[pos] == '{') {
                val alternativeEnd = text.indexOf('}', pos)
                if (alternativeEnd < 0) {
                    throw SpeParseException("Missing curly brace in alternative")
                }
                val choices = text.substring(pos + 1, alternativeEnd).split('|')
                return alternativeEnd + 1 to SpeAlternativeNode(choices.map { parseNodes(language, it) })
            }
            else if (text[pos] == '(') {
                val optionalEnd = text.indexOf(')', pos)
                if (optionalEnd < 0) {
                    throw SpeParseException("Missing closing parenthesis in optional")
                }
                val optionalPart = text.substring(pos + 1, optionalEnd)
                return optionalEnd + 1 to SpeOptionalNode(parseNodes(language, optionalPart))
            }
            else if (text[pos] == '!') {
                val (nextPos, nextNode) = parseNextNode(text, pos + 1, language)
                return nextPos to SpeNegateNode(nextNode)
            }
            else {
                val nextPhoneme = language.phonoPhonemeLookup.nextPhoneme(text, pos)
                if (nextPhoneme == "0") {
                    return pos + nextPhoneme.length to SpeRepeatNode(SpeLiteralNode(""))
                } else {
                    return pos + nextPhoneme.length to nodeFromPhoneme(nextPhoneme, language)
                }
            }
        }

        private fun nodeFromPhoneme(nextPhoneme: String, language: Language): SpeNode {
            if (nextPhoneme == "#") return SpeWordBoundaryNode
            if (nextPhoneme.length == 1 && nextPhoneme[0].isUpperCase()) {
                return SpePhonemeClassNode(
                    language,
                    language.phonemeClassByName(nextPhoneme) ?: throw SpeParseException("Class $nextPhoneme not found")
                )
            }
            return SpeLiteralNode(nextPhoneme)
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
