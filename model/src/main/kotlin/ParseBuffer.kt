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

    val tail: String get() = s.substring(pos)

    fun parseParameter(language: Language): Pair<PhonemeClass?, String?> {
        val tail = s.substring(pos)
        if (s[pos] == '\'') {
            return null to tail.removePrefix("'").removeSuffix("'")
        }
        val characterClass = language.phonemeClassByName(tail.removePrefix(LeafRuleCondition.indefiniteArticle))
            ?: throw RuleParseException("Unrecognized character class $tail")
        return characterClass to null
    }

    fun nextWord(): String? {
        if (pos == s.length) return null
        val nextSpace = s.indexOf(' ', pos).takeIf { it >= 0 } ?: s.length
        val result = s.substring(pos, nextSpace)
        pos = nextSpace
        consumeWhitespace()
        return result
    }

    fun <T : Any> tryParse(callback: () -> T?): T? {
        val mark = pos
        return callback().also { if (it == null) pos = mark }
    }
}