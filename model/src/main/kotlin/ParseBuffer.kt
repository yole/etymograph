package ru.yole.etymograph

class ParseBuffer(val s: String) {
    private var pos = 0

    fun consume(prefix: String): Boolean {
        if (s.startsWith(prefix, pos)) {
            pos += prefix.length
            consumeWhitespace()
            return true
        }
        return false
    }

    private fun consumeWhitespace() {
        while (pos < s.length && s[pos].isWhitespace()) {
            pos++
        }
    }

    fun consumeQuoted(): String? {
        if (s[pos] == '\'') {
            pos++
            val endQuote = s.indexOf('\'', pos)
            if (endQuote < 0) {
                throw RuleParseException("Closing quote expected")
            }
            val result = s.substring(pos, endQuote)
            pos = endQuote + 1
            consumeWhitespace()
            return result
        }
        return null
    }

    fun parseParameter(language: Language): Pair<PhonemeClass?, String?> {
        consumeQuoted()?.let { return null to it }
        consume(LeafRuleCondition.indefiniteArticle)
        var phonemeClassName = nextWord() ?: throw RuleParseException("Phoneme class name expected")
        var mark = pos
        while (true) {
            val nextPhonemeClass = nextWord()
            if (nextPhonemeClass == null || language.phonemeClassByName(nextPhonemeClass) == null) {
                pos = mark
                break
            }
            phonemeClassName = "$phonemeClassName $nextPhonemeClass"
            mark = pos
        }

        val characterClass = language.phonemeClassByName(phonemeClassName)
            ?: throw RuleParseException("Unrecognized character class $phonemeClassName")
        return characterClass to null
    }

    fun nextWord(): String? {
        if (pos == s.length) return null
        val nextSpace = s.indexOfAny(worDelimiters, pos).takeIf { it >= 0 } ?: s.length
        val result = s.substring(pos, nextSpace)
        pos = nextSpace
        consumeWhitespace()
        return result
    }

    fun <T : Any> tryParse(callback: () -> T?): T? {
        val mark = pos
        return callback().also { if (it == null) pos = mark }
    }

    fun fail(message: String): Nothing {
        throw RuleParseException("$message in $s")
    }

    companion object {
        val worDelimiters = charArrayOf(' ', ':', ')')
    }
}