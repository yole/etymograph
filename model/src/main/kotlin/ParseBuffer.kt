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

    private fun consumeQuoted(): String? {
        if (pos < s.length && s[pos] == '\'') {
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

    fun parsePhonemePattern(language: Language): PhonemePattern? {
        consumeQuoted()?.let { return PhonemePattern(null, it) }
        consume(LeafRuleCondition.indefiniteArticle)
        val characterClass = parsePhonemeClass(language, false) ?: return null
        return PhonemePattern(characterClass, consumeQuoted())
    }

    fun parsePhonemeClass(language: Language, allowSound: Boolean): PhonemeClass? {
        if (consume("sound") && allowSound) {
            return null
        }
        else {
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
            if (characterClass == null) {
                if (!allowSound) return null
                throw RuleParseException("Unrecognized character class $phonemeClassName")
            }
            return characterClass
        }
    }

    fun nextWord(): String? {
        if (pos == s.length) return null
        val nextSpace = s.indexOfAny(worDelimiters, pos).takeIf { it >= 0 } ?: s.length
        if (nextSpace == pos) return null
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
        throw RuleParseException("$message in $s at $pos")
    }

    companion object {
        val worDelimiters = charArrayOf(' ', ':', ')')
    }
}