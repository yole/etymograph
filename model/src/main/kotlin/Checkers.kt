package ru.yole.etymograph

data class ConsistencyCheckerIssue(val description: String)

interface ConsistencyChecker {
    fun check(repo: GraphRepository, language: Language, report: (ConsistencyCheckerIssue) -> Unit)
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
                report(ConsistencyCheckerIssue("POS not in list for word $word"))
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

val consistencyCheckers = listOf(
    PosChecker, GlossChecker
)
