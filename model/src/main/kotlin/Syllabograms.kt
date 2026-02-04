package ru.yole.etymograph

import kotlinx.serialization.Serializable

enum class SyllabogramType {
    Syllabogram,
    Logogram,
    LogogramAlt,
    Determinative
}

@Serializable
data class Syllabogram(val text: String, val type: SyllabogramType)

@Serializable
data class SyllabogramSequence(val syllabograms: List<Syllabogram>)

abstract class SyllabogramSyntax {
    abstract fun parse(buffer: String): SyllabogramSequence
    abstract fun render(sequence: SyllabogramSequence): String
}

object TlhDigSyllabogramSyntax : SyllabogramSyntax() {
    override fun parse(buffer: String): SyllabogramSequence {
        val syllabograms = mutableListOf<Syllabogram>()
        var start = 0
        var isAlt = false
        var afterHyphenInLogogram = false

        fun flush(endExclusive: Int, delimiter: Char? = null) {
            if (endExclusive <= start) return
            val part = buffer.substring(start, endExclusive)
            val syllabogram = createSyllabogram(part, isAlt || afterHyphenInLogogram)
            syllabograms.add(syllabogram)
            afterHyphenInLogogram = when (delimiter) {
                '-' -> isLogogram(syllabogram.type)
                '.' -> afterHyphenInLogogram // stays in logogram sequence
                else -> false
            }
            isAlt = false
        }

        var i = 0
        while (i < buffer.length) {
            val ch = buffer[i]
            when {
                ch == '^' -> {
                    flush(i)
                    val endCaret = buffer.indexOf('^', i + 1)
                    if (endCaret < 0) {
                        // No closing caret; treat '^' as a regular character.
                        i++
                        continue
                    }
                    val detText = buffer.substring(i + 1, endCaret)
                    syllabograms.add(Syllabogram(detText, SyllabogramType.Determinative))
                    // Determinative acts as a boundary; reset token state.
                    start = endCaret + 1
                    isAlt = false
                    afterHyphenInLogogram = false
                    i = start
                    continue
                }
                ch == '_' && i == start -> {
                    isAlt = true
                    start++
                    i++
                    continue
                }
                ch == '-' || ch == '.' -> {
                    flush(i, ch)
                    start = i + 1
                    i++
                    continue
                }
                else -> i++
            }
        }
        flush(buffer.length)
        return SyllabogramSequence(syllabograms)
    }

    private fun createSyllabogram(text: String, isAlt: Boolean): Syllabogram {
        val isLogogram = text.all { it.isUpperCase() || it.isDigit()}
        val type = when {
            isAlt -> SyllabogramType.LogogramAlt
            isLogogram -> SyllabogramType.Logogram
            else -> SyllabogramType.Syllabogram
        }
        return Syllabogram(text, type)
    }

    override fun render(sequence: SyllabogramSequence): String {
        if (sequence.syllabograms.isEmpty()) return ""
        val result = StringBuilder()

        fun appendSyllabogram(index: Int, syllabogram: Syllabogram) {
            when (syllabogram.type) {
                SyllabogramType.Determinative -> result.append('^').append(syllabogram.text).append('^')
                SyllabogramType.LogogramAlt -> {
                    val prev = sequence.syllabograms.getOrNull(index - 1)
                    if (prev == null || !isLogogram(prev.type)) {
                        result.append('_')
                    }
                    result.append(syllabogram.text)
                }
                else -> result.append(syllabogram.text)
            }
        }

        appendSyllabogram(0, sequence.syllabograms[0])
        for (i in 1 until sequence.syllabograms.size) {
            val prev = sequence.syllabograms[i - 1]
            val current = sequence.syllabograms[i]

            if (prev.type != SyllabogramType.Determinative && current.type != SyllabogramType.Determinative) {
                if (isLogogram(prev.type) && isLogogram(current.type)) {
                    if (current.type == SyllabogramType.LogogramAlt) {
                        result.append("-")
                    }
                    else {
                        result.append(".")
                    }
                } else {
                    result.append("-")
                }
            }
            appendSyllabogram(i, current)
        }
        return result.toString()
    }

    private fun isLogogram(type: SyllabogramType): Boolean =
        type == SyllabogramType.Logogram || type == SyllabogramType.LogogramAlt
}
