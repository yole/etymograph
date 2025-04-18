package ru.yole.etymograph

import java.util.*

data class ConsistencyCheckerIssue(val description: String)

interface ConsistencyChecker {
    fun check(repo: GraphRepository, language: Language, report: (ConsistencyCheckerIssue) -> Unit)
}

object WordTextChecker : ConsistencyChecker {
    override fun check(repo: GraphRepository, language: Language, report: (ConsistencyCheckerIssue) -> Unit) {
        for (word in repo.allWords(language)) {
            var charactersNotInInventory = false
            val text = word.asPhonemic().text
            language.phonoPhonemeLookup.iteratePhonemes(text) { startIndex, endIndex, phoneme ->
                if (phoneme == null && text.substring(startIndex, endIndex) != "-") charactersNotInInventory = true
            }
            if (charactersNotInInventory) {
                report(ConsistencyCheckerIssue("Word text contains characters outside of inventory for $word"))
            }
        }
    }
}

object PosChecker : ConsistencyChecker {
    override fun check(repo: GraphRepository, language: Language, report: (ConsistencyCheckerIssue) -> Unit) {
        for (word in repo.allWords(language)) {
            val baseWordLink = word.baseWordLink(repo)
            val variationOf = word.getVariationOf(repo)
            if ((baseWordLink != null || variationOf != null) && word.pos != null) {
                report(ConsistencyCheckerIssue("POS specified for derived word $word"))
            }
            else if (baseWordLink == null && variationOf == null && word.pos == null &&
                repo.findCompoundsByCompoundWord(word).isEmpty())
            {
                report(ConsistencyCheckerIssue("No POS specified for word $word"))
            }
            else if (word.pos != null && language.pos.none { it.abbreviation == word.pos }) {
                report(ConsistencyCheckerIssue("POS '${word.pos}' not in list for word $word"))
            }
        }
    }
}

object GlossChecker : ConsistencyChecker {
    override fun check(repo: GraphRepository, language: Language, report: (ConsistencyCheckerIssue) -> Unit) {
        for (word in repo.allWords(language)) {
            val baseWordLink = word.baseWordLink(repo)
            val variationOf = word.getVariationOf(repo)
            if ((baseWordLink != null || variationOf != null) && word.gloss != null) {
                report(ConsistencyCheckerIssue("Gloss specified for derived word $word"))
            }
            else if (baseWordLink == null && variationOf == null && word.gloss == null &&
                word.pos != KnownPartsOfSpeech.properName.abbreviation && repo.findCompoundsByCompoundWord(word).isEmpty())
            {
                report(ConsistencyCheckerIssue("No gloss specified for word $word"))
            }
        }
    }
}

object WordClassChecker : ConsistencyChecker {
    override fun check(repo: GraphRepository, language: Language, report: (ConsistencyCheckerIssue) -> Unit) {
        for (word in repo.allWords(language)) {
            for (cls in word.classes) {
                val wordClass = language.wordClasses.find { it.values.any { v -> v.abbreviation == cls } }
                if (wordClass == null) {
                    report(ConsistencyCheckerIssue("Word class '$cls''not found for $word"))
                }
                else if (word.pos !in wordClass.pos) {
                    report(ConsistencyCheckerIssue("Word POS does not match POS of word class '$cls' for $word"))
                }
            }
        }
    }
}

object LinkChecker : ConsistencyChecker {
    override fun check(repo: GraphRepository, language: Language, report: (ConsistencyCheckerIssue) -> Unit) {
        for (word in repo.allWords(language)) {
            val links = repo.getLinksFrom(word)
            for (link in links) {
                if (link.type == Link.Derived && link.rules.isEmpty()) {
                    report(ConsistencyCheckerIssue("Derived link with no rules for word $word"))
                }
                var expectedPOS = (link.toEntity as? Word)?.pos
                if (expectedPOS != null) {
                    for (rule in link.rules) {
                        val rulePOS = repo.paradigmForRule(rule)?.pos ?: rule.fromPOS
                        if (rulePOS.isNotEmpty() && expectedPOS !in rulePOS) {
                            report(ConsistencyCheckerIssue("Word POS does not match rule POS for link from $word, rule ${rule.name}"))
                        }
                        expectedPOS = rule.toPOS
                    }
                }
            }
        }
    }
}

object CorpusTextChecker : ConsistencyChecker {
    override fun check(repo: GraphRepository, language: Language, report: (ConsistencyCheckerIssue) -> Unit) {
        for (corpusText in repo.corpusTextsInLanguage(language)) {
            for (line in corpusText.mapToLines(repo)) {
                for (corpusWord in line.corpusWords) {
                    if (corpusWord.word != null &&
                        corpusWord.word.text.lowercase(Locale.FRANCE) != corpusWord.normalizedText.lowercase(Locale.FRANCE))
                    {
                        report(ConsistencyCheckerIssue("Corpus word text '${corpusWord.normalizedText}' doesn't match word text '${corpusWord.word.text}' in ${corpusText.title}"))
                    }
                }
            }
        }
    }
}

object RuleChecker : ConsistencyChecker {
    override fun check(repo: GraphRepository, language: Language, report: (ConsistencyCheckerIssue) -> Unit) {
        for (rule in repo.allRules().filter { it.toLanguage == language }) {
            for (pos in rule.fromPOS) {
                if (rule.fromLanguage.pos.none { it.abbreviation == pos }) {
                    report(ConsistencyCheckerIssue("Unknown rule from POS '$pos' in ${rule.name}"))
                }
            }
            checkLogic(rule, report)
        }
    }

    private fun checkLogic(rule: Rule, report: (ConsistencyCheckerIssue) -> Unit) {
        for (preInstruction in rule.logic.preInstructions) {
            checkInstruction(rule, preInstruction, report)
        }
        for (branch in rule.logic.branches) {
            checkCondition(rule, branch.condition, report)
            for (instruction in branch.instructions) {
                checkInstruction(rule, instruction, report)
            }
        }
        for (postInstruction in rule.logic.postInstructions) {
            checkInstruction(rule, postInstruction, report)
        }
    }

    private fun checkInstruction(rule: Rule, instruction: RuleInstruction, report: (ConsistencyCheckerIssue) -> Unit) {
        if (instruction is SpeInstruction) {
            checkSpePattern(rule, instruction.pattern, report)
        }
    }

    private fun checkCondition(rule: Rule, condition: RuleCondition, report: (ConsistencyCheckerIssue) -> Unit) {
        if (condition is RelativePhonemeRuleCondition) {
            val pattern = condition.phonemePattern
            if (pattern.literal != null &&
                rule.fromLanguage.phonemes.find { it.effectiveSound == pattern.literal } == null)
            {
                report(ConsistencyCheckerIssue("Rule ${rule.name} refers to unknown phoneme ${pattern.literal}"))
            }
        }
    }

    private fun checkSpePattern(rule: Rule, pattern: SpePattern, report: (ConsistencyCheckerIssue) -> Unit) {
        for (speNode in pattern.before) {
            checkSpeNode(rule, rule.fromLanguage, speNode, report)
        }
        for (speNode in pattern.after) {
            checkSpeNode(rule, rule.toLanguage, speNode, report)
        }
        for (speNode in pattern.preceding) {
            checkSpeNode(rule, rule.fromLanguage, speNode, report)
        }
        for (speNode in pattern.following) {
            checkSpeNode(rule, rule.fromLanguage, speNode, report)
        }
    }

    private fun checkSpeNode(
        rule: Rule,
        language: Language,
        speNode: SpeNode,
        report: (ConsistencyCheckerIssue) -> Unit
    ) {
        if (speNode is SpeLiteralNode) {
            val phoneme = language.phonemes.find { it.effectiveSound == speNode.text }
            if (phoneme == null) {
                report(ConsistencyCheckerIssue("Rule ${rule.name} refers to unknown phoneme ${speNode.text}"))
            }
        }
        if (speNode is SpeAlternativeNode) {
            if (speNode.choices.size == 1) {
                report(ConsistencyCheckerIssue("Rule ${rule.name} contains alternative node with single choice: $speNode"))
            }
            for (choice in speNode.choices) {
                for (element in choice) {
                    checkSpeNode(rule, language, element, report)
                }
            }
        }
    }
}

val consistencyCheckers = listOf(
    WordTextChecker, PosChecker, GlossChecker, WordClassChecker, LinkChecker, CorpusTextChecker, RuleChecker
)
