package ru.yole.etymograph

import java.text.Collator
import java.text.Normalizer
import java.util.*

open class InMemoryGraphRepository : GraphRepository() {
    protected val languages = mutableMapOf<String, Language>()
    protected val corpus = mutableListOf<CorpusText>()
    private val words = mutableMapOf<Language, MutableMap<String, MutableList<Word>>>()
    val allLangEntities = mutableListOf<LangEntity?>()
    private val linksFrom = mutableMapOf<Int, MutableList<Link>>()
    private val linksTo = mutableMapOf<Int, MutableList<Link>>()
    protected val compounds = mutableMapOf<Int, MutableList<Compound>>()
    protected val translations = mutableMapOf<Int, MutableList<Translation>>()
    protected val rules = mutableListOf<Rule>()
    protected val paradigms = mutableListOf<Paradigm?>()
    private val paradigmsByLanguage = mutableMapOf<Language, MutableList<Paradigm>>()

    protected val publications = mutableListOf<Publication?>()

    override val id: String
        get() = ""

    override val name: String
        get() = ""

    override fun addLanguage(language: Language) {
        languages[language.shortName] = language
    }

    override fun allLanguages(): Iterable<Language> {
        return languages.values
    }

    override fun languageByShortName(languageShortName: String): Language? {
        return languages[languageShortName]
    }

    override fun langEntityById(id: Int): LangEntity? =
        allLangEntities.getOrNull(id)

    override fun addPhoneme(
        language: Language,
        graphemes: List<String>,
        sound: String?,
        classes: Set<String>,
        historical: Boolean,
        source: List<SourceRef>,
        notes: String?
    ): Phoneme {
        return Phoneme(allLangEntities.size, graphemes, sound, classes, historical, source, notes).also {
            language.phonemes += it
            allLangEntities += it
        }
    }

    override fun deletePhoneme(language: Language, phoneme: Phoneme) {
        language.phonemes -= phoneme
        deleteLangEntity(phoneme)
    }

    override fun addCorpusText(
        text: String,
        title: String?,
        language: Language,
        source: List<SourceRef>,
        notes: String?
    ): CorpusText {
        return CorpusText(allLangEntities.size, text, title, language, emptyList(), source, notes).also {
            corpus += it
            allLangEntities += it
        }
    }

    override fun corpusTextById(id: Int): CorpusText? {
        return allLangEntities.getOrNull(id) as? CorpusText
    }

    override fun addTranslation(corpusText: CorpusText, text: String, source: List<SourceRef>): Translation {
        val translation = Translation(allLangEntities.size, corpusText, text, source, null)
        allLangEntities += translation
        storeTranslation(corpusText, translation)
        return translation
    }

    protected fun storeTranslation(corpusText: CorpusText, translation: Translation) {
        val textTranslations = translations.getOrPut(corpusText.id) { mutableListOf() }
        textTranslations.add(translation)
    }

    override fun translationsForText(corpusText: CorpusText): List<Translation> {
        return translations[corpusText.id] ?: emptyList()
    }

    override fun wordsByText(lang: Language, text: String): List<Word> {
        val wordsInLang = words[lang] ?: return emptyList()
        return wordsInLang[lang.normalizeWord(text)] ?: emptyList()
    }

    override fun wordsByTextFuzzy(lang: Language, text: String): List<Word> {
        val wordsInLang = words[lang] ?: return emptyList()
        val matchText = lang.normalizeWord(text).removeDiacritics()
        return wordsInLang.values.flatten().filter { it.text.removeDiacritics() == matchText }
    }

    override fun wordById(id: Int): Word? {
        return allLangEntities.getOrNull(id) as? Word
    }

    override fun dictionaryWords(lang: Language): List<Word> {
        return filteredWords(lang, WordKind.NORMAL)
    }

    override fun allWords(lang: Language): List<Word> {
        return words[lang]?.values?.flatten() ?: emptyList()
    }

    private fun classifyWord(word: Word): WordKind {
        return when {
            word.reconstructed -> WordKind.RECONSTRUCTED
            word.pos == "NP" -> WordKind.NAME
            isCompound(word) -> WordKind.COMPOUND
            word.gloss == null -> WordKind.DERIVED
            word.hasGrammarCategory() -> WordKind.DERIVED
            else -> WordKind.NORMAL
        }
    }

    override fun isCompound(word: Word): Boolean {
        val compounds = compounds[word.id] ?: return false
        return compounds.any {
            it.components.any { c -> !c.isRoot() } && !it.isDerivation()
        }
    }

    private fun Word.hasGrammarCategory(): Boolean {
        val suffix = gloss?.substringAfterLast('.', "")
        return !suffix.isNullOrEmpty() && suffix.all { it.isUpperCase() || it.isDigit() }
    }

    override fun filteredWords(lang: Language, kind: WordKind): List<Word> {
        val wordsInLang = words[lang] ?: return emptyList()
        return wordsInLang.flatMap { it.value }
            .filter { classifyWord(it) == kind }
            .sortedWith { o1, o2 -> Collator.getInstance(Locale.FRANCE).compare(o1.text, o2.text) }
    }

    private fun Word.isRoot() = text.all { c -> c.isUpperCase() || c == '-' }

    override fun findAttestations(word: Word): List<Attestation> {
        val allDerivedWords = collectDerivedWords(word)
        val result = mutableListOf<Attestation>()
        corpusText@ for (corpusText in corpusTextsInLanguage(word.language)) {
            for (derivedWord in allDerivedWords) {
                if (corpusText.containsWord(derivedWord)) {
                    result.add(Attestation(derivedWord, corpusText))
                    continue@corpusText
                }
            }
        }
        return result
    }

    private fun collectDerivedWords(word: Word): Collection<Word> {
        val result = mutableSetOf<Word>()
        result.add(word)
        for (link in getLinksTo(word)) {
            if (link.fromEntity is Word && (link.type == Link.Derived || link.type == Link.Variation)) {
                result.add(link.fromEntity)
            }
        }
        for (compound in findCompoundsByComponent(word)) {
            result.add(compound.compoundWord)
        }
        return result
    }

    data class ParseCandidateKey(val text: String, val categories: String)

    override fun findParseCandidates(word: Word): List<ParseCandidate> {
        val result = findParseCandidates(
            word.language, word.text, word.pos, emptyList(),
            collectDerivedWordGrammaticalCategories(word), true
        )
        val uniqueMap = mutableMapOf<ParseCandidateKey, ParseCandidate>()
        for (parseCandidate in result) {
            val key = ParseCandidateKey(parseCandidate.text, parseCandidate.categories)
            if (key !in uniqueMap) {
                uniqueMap[key] = parseCandidate
            }
        }
        return uniqueMap.values.filter { it.word != null }.toList() + uniqueMap.values.filter { it.word == null }.toList()
    }

    private fun findParseCandidates(language: Language, text: String, pos: String?, prevRules: List<Rule>,
                                    excludeGrammaticalCategories: Set<WordCategory>, recurse: Boolean): List<ParseCandidate> {
        return rules
            .filter { rule ->
                rule.fromLanguage == language && rule.toLanguage == language &&
                        considerForParseCandidates(rule) &&
                        ruleMatchesPOS(pos, rule) &&
                        rule.addedGrammaticalCategories().none { it in excludeGrammaticalCategories }
            }
            .flatMap { rule ->
                rule.reverseApply(Word(-1, text, language, pos=pos), this)
                    .filter {
                        language.normalizeWord(it) != language.normalizeWord(text) &&
                                (isAcceptableWord(language, it) || wordsByText(language, "$it-").isNotEmpty())
                    }
                    .flatMap { candidateText ->
                        val w = wordsByText(language, candidateText)
                            .ifEmpty { wordsByText(language, "$candidateText-") }
                            .filter { w -> ruleMatchesPOS(w.pos, rule) && !grammaticalCategoriesIntersect(w, rule.addedGrammaticalCategories())}

                        if (w.isNotEmpty())
                            w.map { ParseCandidate(restoreCase(candidateText, text), listOf(rule) + prevRules, it.pos, it) }
                        else
                            listOf(ParseCandidate(restoreCase(candidateText, text), listOf(rule) + prevRules, rule.fromPOS.firstOrNull(), null))
                    }
                    .flatMap {
                        if (recurse) {
                            val recurseExcludeGrammaticalCategories = excludeGrammaticalCategories + rule.addedGrammaticalCategories().toSet()
                            listOf(it) + findParseCandidates(language, it.text, it.pos, it.rules, recurseExcludeGrammaticalCategories, false)
                        }
                        else {
                            listOf(it)
                        }
                    }
            }
    }

    private fun grammaticalCategoriesIntersect(w: Word, addedGrammaticalCategories: List<WordCategory>): Boolean {
        val suffix = w.grammaticalCategorySuffix(this) ?: return false
        val gc = parseCategoryValues(w.language, suffix)
        return gc.any { it != null && it.category in addedGrammaticalCategories }
    }

    private fun considerForParseCandidates(rule: Rule) =
        rule.toPOS != null || !rule.addedCategories.isNullOrEmpty()

    private fun ruleMatchesPOS(pos: String?, rule: Rule): Boolean {
        if (pos == null) return true
        if (rule.toPOS != null) {
            return rule.toPOS == pos
        }
        if (rule.fromPOS.isNotEmpty()) {
            return pos in rule.fromPOS
        }
        return true
    }

    private fun collectDerivedWordGrammaticalCategories(word: Word): Set<WordCategory> {
        return getLinksTo(word)
            .filter { it.fromEntity is Word }
            .flatMap { it.rules.lastOrNull()?.addedGrammaticalCategories() ?: emptyList() }
            .toSet()
    }

    override fun restoreSegments(word: Word): Word {
        val baseWordLink = word.baseWordLink(this)
        if (baseWordLink != null) {
            if (baseWordLink.rules.isEmpty()) {
                return word
            }

            val baseWord = baseWordLink.toEntity as Word
            if (baseWord.language == word.language) {
                val baseWordWithSegments = restoreSegments(baseWord)
                val restoredWord = baseWordLink.applyRules(baseWordWithSegments, this)
                if (word.language.isNormalizedEqual(restoredWord, word)) {
                    return restoredWord
                }
            }
        }

        val compound = findCompoundsByCompoundWord(word).singleOrNull()
        if (compound != null) {
            val segments = mutableListOf<WordSegment>()
            var index = 0
            for (component in compound.components) {
                val normalizedComponentText = component.text.removeSuffix("-")
                val componentLength = normalizedComponentText.length
                if (index + componentLength > word.text.length || word.text.substring(index, index + componentLength) != normalizedComponentText) {
                    break
                }
                segments.add(WordSegment(index, componentLength, null, component, null, "clitic" in component.classes))
                index += componentLength
            }
            word.segments = segments
        }

        return word
    }

    fun isAcceptableWord(language: Language, wordText: String): Boolean {
        if (wordText.isEmpty()) return false
        val word = Word(-1, wordText, language)
        val vowels = language.phonemeClassByName(PhonemeClass.vowelClassName)
        val phonemes = PhonemeIterator(word, this)
        if (language.syllableStructures.isNotEmpty() && vowels != null) {
            val syllables = breakIntoSyllables(word)
            if (syllables.isEmpty()) {
                return false
            }
            for (syllable in syllables) {
                if (analyzeSyllableStructure(vowels, phonemes, syllable) !in language.syllableStructures) {
                    return false
                }
            }
        }
        return matchesPhonotactics(language, wordText)
    }

    override fun matchesPhonotactics(lang: Language, text: String): Boolean {
        val rule = lang.phonotacticsRule?.resolve()
        if (rule != null) {
            val result = rule.apply(Word(-1, text, lang), this)
            return DISALLOW_CLASS !in result.classes
        }
        return true
    }

    override fun isHomonym(word: Word): Boolean {
        return wordsByText(word.language, word.text).size > 1
    }

    override fun allCorpusTexts(): Iterable<CorpusText> {
        return corpus
    }

    override fun corpusTextsInLanguage(lang: Language): Iterable<CorpusText> {
        return corpus.filter { it.language == lang }
    }

    override fun findOrAddWord(
        text: String,
        language: Language,
        gloss: String?,
        fullGloss: String?,
        pos: String?,
        classes: List<String>,
        reconstructed: Boolean,
        source: List<SourceRef>,
        notes: String?
    ): Word {
        val wordsByText = mapOfWordsByText(language, text)
        wordsByText.find { it.getOrComputeGloss(this) == gloss || gloss.isNullOrEmpty() }?.let {
            return it
        }
        return addWord(text, language, gloss, fullGloss, pos, classes, reconstructed, source, notes)
    }

    override fun updateWordText(word: Word, text: String) {
        mapOfWordsByText(word.language, word.text).remove(word)
        word.text = text
        mapOfWordsByText(word.language, text).add(word)
    }

    override fun addWord(
        text: String,
        language: Language,
        gloss: String?,
        fullGloss: String?,
        pos: String?,
        classes: List<String>,
        reconstructed: Boolean,
        source: List<SourceRef>,
        notes: String?
    ): Word {
        if (text.isBlank()) {
            throw IllegalArgumentException("Cannot create word with empty text")
        }
        if ('?' in text || '/' in text) {
            throw IllegalArgumentException("Word text may not contain ? or /")
        }
        val wordsByText = mapOfWordsByText(language, text)
        return Word(allLangEntities.size, text, language, gloss, fullGloss, pos, classes, reconstructed, source, notes).also {
            allLangEntities.add(it)
            wordsByText.add(it)
        }
    }

    override fun deleteWord(word: Word) {
        for (corpusText in corpus) {
            corpusText.removeWord(word)
        }

        val wordsByText = mapOfWordsByText(word.language, word.text)
        wordsByText.remove(word)

        val compoundsOfWord = compounds.remove(word.id)
        if (compoundsOfWord != null) {
            for (compound in compoundsOfWord) {
                deleteCompound(compound)
            }
        }
        for (compoundList in compounds.values) {
            for (compound in compoundList) {
                compound.components.remove(word)
            }
        }

        deleteLangEntity(word)
    }

    private fun deleteLangEntity(langEntity: LangEntity) {
        linksFrom[langEntity.id]?.let {
            for (link in it.toList()) {
                deleteLink(link.fromEntity, link.toEntity, link.type)
            }
        }
        linksTo[langEntity.id]?.let {
            for (link in it.toList()) {
                deleteLink(link.fromEntity, link.toEntity, link.type)
            }
        }
        allLangEntities[langEntity.id] = null
    }

    override fun deleteRule(rule: Rule) {
        for (paradigm in paradigms) {
            paradigm?.removeRule(rule)
        }
        for (ruleSequence in allLangEntities.filterIsInstance<RuleSequence>()) {
            ruleSequence.steps = ruleSequence.steps.filter { it.ruleId != rule.id }
        }
        rules.remove(rule)
        deleteLangEntity(rule)
    }

    override fun findReferencingParadigms(rule: Rule): List<Pair<Paradigm, String>> {
        val result = mutableListOf<Pair<Paradigm, String>>()
        for (paradigm in paradigms) {
            if (paradigm?.preRule == rule) {
                result.add(paradigm to "pre-rule")
            }
            if (paradigm?.postRule == rule) {
                result.add(paradigm to "post-rule")
            }
        }
        return result
    }

    override fun findReferencingRules(rule: Rule): List<Rule> {
        return allRules().filter { rule in it.referencedRules() }
    }

    override fun allSequences(): List<RuleSequence> {
        return allLangEntities.filterIsInstance<RuleSequence>()
    }

    override fun addRuleSequence(name: String, fromLanguage: Language, toLanguage: Language, rules: List<RuleSequenceStep>): RuleSequence {
        return RuleSequence(allLangEntities.size, name, fromLanguage, toLanguage,
            rules.map { RuleSequenceStepRef(it.rule.id, it.alternative?.id, it.optional, it.dispreferred) }, emptyList(), null
        ).also {
            allLangEntities.add(it)
        }
    }

    override fun ruleSequencesForLanguage(language: Language): List<RuleSequence> {
        return allLangEntities.filterIsInstance<RuleSequence>().filter { it.toLanguage == language }
    }

    override fun ruleSequencesFromLanguage(language: Language): List<RuleSequence> {
        return allLangEntities.filterIsInstance<RuleSequence>().filter { it.fromLanguage == language }
    }

    override fun ruleSequenceByName(name: String): RuleSequence? {
        return allLangEntities.filterIsInstance<RuleSequence>().find { it.name == name }
    }

    override fun applyRuleSequence(link: Link, sequence: RuleSequence): Consistency {
        val word = (link.toEntity as Word).normalized
        val expectWord = (link.fromEntity as Word).normalized
        val wasConsistent = expectWord.language.isNormalizedEqual(link.applyRules(word, this), expectWord)

        val applicableRules = mutableListOf<Rule>()
        var isConsistent = false
        val targetWord = applyRuleSequence(word, sequence, expectWord, applicableRules)
        if (targetWord != null) {
            link.rules = applicableRules
            link.sequence = sequence
            isConsistent = expectWord.language.isNormalizedEqual(targetWord, expectWord)
        }

        return when {
            wasConsistent && isConsistent -> Consistency.CONSISTENT
            !wasConsistent && isConsistent -> Consistency.BECOMES_CONSISTENT
            wasConsistent && !isConsistent -> Consistency.BECOMES_INCONSISTENT
            else -> Consistency.INCONSISTENT
        }
    }

    override fun reapplyRuleSequence(sequence: RuleSequence): Map<Consistency, List<Word>> {
        val allSequences = findSequencesContainingSequence(sequence)
        val directLinks = linksFrom.values.flatten().filter { it.sequence in allSequences }
        val result = mutableMapOf<Consistency, MutableList<Word>>()
        for (directLink in directLinks) {
            val consistency = applyRuleSequence(directLink, directLink.sequence!!)
            result.getOrPut(consistency) { mutableListOf() }.add(directLink.fromEntity as Word)
        }
        return result
    }

    override fun suggestDeriveRuleSequences(word: Word): List<RuleSequence> {
        val existingDerivedWordLanguages = getLinksTo(word)
            .filter { it.type == Link.Origin }
            .map { it.fromEntity }
            .filterIsInstance<Word>()
            .mapTo(mutableSetOf()) { it.language }
        return ruleSequencesFromLanguage(word.language).filter {
            it.toLanguage !in existingDerivedWordLanguages
        }
    }

    override fun deriveThroughRuleSequence(word: Word, sequence: RuleSequence): Word? {
        val applicableRules = mutableListOf<Rule>()
        val targetWord = applyRuleSequence(word, sequence, null, applicableRules) ?: return null
        val newWord = findOrAddWord(
            targetWord.text,
            sequence.toLanguage,
            word.gloss,
            pos = word.pos,
            classes = word.classes
        )
        addLink(newWord, word, Link.Origin, applicableRules, sequence = sequence)
        return newWord
    }

    override fun findSequencesContainingRule(rule: Rule): List<RuleSequence> {
        return allLangEntities.filterIsInstance<RuleSequence>().filter { rule in it.resolveRules(this) }
    }

    override fun findSequencesContainingSequence(ruleSequence: RuleSequence): List<RuleSequence> {
        val result = mutableListOf<RuleSequence>()
        collectSequencesContainingSequence(ruleSequence, result)
        return result
    }

    private fun collectSequencesContainingSequence(ruleSequence: RuleSequence, result: MutableList<RuleSequence>) {
        result.add(ruleSequence)
        for (sequence in allLangEntities.filterIsInstance<RuleSequence>()) {
            if (sequence.steps.any { it.ruleId == ruleSequence.id }) {
                collectSequencesContainingSequence(sequence, result)
            }
        }
    }

    override fun findDerivationsWithSequence(sequence: RuleSequence): List<List<Link>> {
        val directLinks = linksFrom.values.flatten().filter { it.sequence == sequence }.map { listOf(it) }
        val candidates = linksFrom.values.flatten()
            .filter {
                it.type == Link.Origin &&
                    it.sequence == null &&
                    (it.toEntity as? Word)?.language == sequence.fromLanguage &&
                    (it.fromEntity as? Word)?.language == sequence.toLanguage
            }
            .map { listOf(it) }
        val steps = sequence.steps.map { langEntityById(it.ruleId) }
        if (steps.all { it is RuleSequence }) {
            val linksWithFirstSequence = findDerivationsWithSequence(steps.first() as RuleSequence)
            @Suppress("UNCHECKED_CAST")
            return directLinks + candidates + buildFollowupSteps(linksWithFirstSequence, steps as List<RuleSequence>)
        }
        return directLinks + candidates
    }

    private fun buildFollowupSteps(links: List<List<Link>>, steps: List<RuleSequence>): List<List<Link>> {
        return links.mapNotNull { linkList ->
            var target = linkList.last().fromEntity as Word
            val result = linkList.toMutableList()
            for (ruleSequence in steps.drop(1)) {
                val nextLink = getLinksTo(target).find { it.sequence == ruleSequence }
                    ?: return@mapNotNull null
                result.add(nextLink)
                target = nextLink.fromEntity as Word
            }
            result
        }
    }

    private fun applyRuleSequence(word: Word, sequence: RuleSequence, expectWord: Word?, applicableRules: MutableList<Rule>): Word? {
        if (expectWord != null) {
            data class RuleApplicationVariant(val word: Word, val rules: List<Rule>)

            val steps = sequence.resolveSteps(this)
            var variants = listOf(RuleApplicationVariant(word, emptyList()))
            for (step in steps) {
                variants = variants.flatMap {
                    val next = mutableListOf<RuleApplicationVariant>()
                    val result = (step.rule as Rule).apply(it.word, this)
                    if ('?' !in result.text) {
                        if (step.optional || result.asPhonemic().text == it.word.asPhonemic().text) {
                            next.add(it)
                        }
                        if (result.asPhonemic().text != it.word.asPhonemic().text) {
                            next.add(RuleApplicationVariant(result, it.rules + step.rule))
                        }
                        if (step.alternative != null) {
                            val altResult = (step.alternative as Rule).apply(it.word, this)
                            if (altResult.asPhonemic().text != it.word.asPhonemic().text) {
                                next.add(RuleApplicationVariant(altResult, it.rules + step.alternative))
                            }
                        }
                    }
                    next
                }
            }

            val bestMatch = variants.firstOrNull { it.word.asPhonemic().text == expectWord.text }
                ?: variants.firstOrNull { getSinglePhonemeDifference(it.word, expectWord) != null }
            if (bestMatch != null) {
                applicableRules.addAll(bestMatch.rules)
                return bestMatch.word
            }
        }
        return applyRuleSequenceVariant(word, sequence.resolveRules(this), applicableRules)
    }

    private fun applyRuleSequenceVariant(
        word: Word,
        rules: List<Rule>,
        applicableRules: MutableList<Rule>
    ): Word? {
        var targetWord = word
        for (rule in rules) {
            val newWord = rule.apply(targetWord, this)
            if ('?' in newWord.text) return null
            if (newWord.asPhonemic().text != targetWord.asPhonemic().text) {
                applicableRules.add(rule)
                targetWord = newWord
            }
        }
        return targetWord
    }

    protected fun mapOfWordsByText(
        language: Language,
        text: String
    ): MutableList<Word> {
        val wordsForLanguage = words.getOrPut(language) { mutableMapOf() }
        val wordsByText = wordsForLanguage.getOrPut(language.normalizeWord(text)) { mutableListOf() }
        return wordsByText
    }

    override fun save() {
    }

    override fun addLink(
        fromEntity: LangEntity, toEntity: LangEntity, type: LinkType, rules: List<Rule>,
        sequence: RuleSequence?,
        source: List<SourceRef>, notes: String?
    ): Link {
        if (fromEntity.id == toEntity.id) {
            throw IllegalArgumentException("Cannot link entity to itself ($fromEntity)")
        }
        val existingLink = findLink(fromEntity, toEntity, type)
        if (existingLink != null) {
            throw IllegalArgumentException("Link already exists")
        }

        val allLinkedEntities = mutableSetOf<Int>()
        collectLinkedEntitiesRecursive(fromEntity, allLinkedEntities, type)
        if (toEntity.id in allLinkedEntities) {
            throw IllegalArgumentException("Cycles in links are not allowed ($fromEntity <-> $toEntity)")
        }

        return createLink(fromEntity, toEntity, type, rules, sequence, source, notes).also {
            linksFrom.getOrPut(it.fromEntity.id) { mutableListOf() }.add(it)
            linksTo.getOrPut(it.toEntity.id) { mutableListOf() }.add(it)
        }
    }

    private fun collectLinkedEntitiesRecursive(fromEntity: LangEntity, seenEntities: MutableSet<Int>, linkType: LinkType) {
        if (fromEntity.id in seenEntities) return
        seenEntities.add(fromEntity.id)
        for (link in getLinksFrom(fromEntity)) {
            if (link.type == linkType) {
                collectLinkedEntitiesRecursive(link.toEntity, seenEntities, linkType)
            }
        }
        for (link in getLinksTo(fromEntity)) {
            if (link.type == linkType) {
                collectLinkedEntitiesRecursive(link.fromEntity, seenEntities, linkType)
            }
        }
    }

    override fun substituteKnownWord(baseWord: Word, derivedWord: Word): Word {
        val links = linksTo[baseWord.id] ?: return derivedWord
        for (link in links) {
            if (link.type == Link.Derived) {
                val fromEntity = link.fromEntity
                if (fromEntity is Word && fromEntity.getOrComputeGloss(this) == derivedWord.gloss) {
                    return fromEntity
                }
            }
        }
        return derivedWord
    }

    override fun deleteLink(fromEntity: LangEntity, toEntity: LangEntity, type: LinkType): Boolean {
        val result = linksFrom.getOrPut(fromEntity.id) { mutableListOf() }.removeIf { it.toEntity == toEntity && it.type == type }
        linksTo.getOrPut(toEntity.id) { mutableListOf() }.removeIf { it.fromEntity == fromEntity && it.type == type }
        return result
    }

    override fun findLink(fromEntity: LangEntity, toEntity: LangEntity, type: LinkType): Link? {
        return linksFrom[fromEntity.id]?.find { it.toEntity == toEntity && it.type == type } ?:
            linksFrom[toEntity.id]?.find { it.toEntity == fromEntity && it.type == type }
    }

    protected open fun createLink(
        fromEntity: LangEntity, toEntity: LangEntity, type: LinkType, rules: List<Rule>,
        sequence: RuleSequence?,
        source: List<SourceRef>, notes: String?
    ): Link {
        return Link(fromEntity, toEntity, type, rules, sequence, source, notes)
    }

    override fun getLinksFrom(entity: LangEntity): Iterable<Link> {
        return linksFrom[entity.id] ?: emptyList()
    }

    override fun getLinksTo(entity: LangEntity): Iterable<Link> {
        return linksTo[entity.id] ?: emptyList()
    }

    override fun createCompound(
        compoundWord: Word,
        components: List<Word>,
        headIndex: Int?,
        source: List<SourceRef>,
        notes: String?
    ): Compound {
        val compound = Compound(allLangEntities.size, compoundWord, components.toMutableList(), headIndex, source, notes)
        compounds.getOrPut(compoundWord.id) { arrayListOf() }.add(compound)
        allLangEntities.add(compound)
        return compound
    }

    override fun findCompoundsByComponent(component: Word): List<Compound> {
        return allLangEntities.filterIsInstance<Compound>().filter { component.id in it.components.map { c -> c.id } }
    }

    override fun findCompoundsByCompoundWord(compoundWord: Word): List<Compound> {
        return compounds[compoundWord.id] ?: emptyList()
    }

    override fun deleteCompound(compound: Compound) {
        val compoundList = compounds[compound.compoundWord.id]
        compoundList?.remove(compound)
        if (compoundList?.isEmpty() == true) {
            compounds.remove(compound.compoundWord.id)
        }
        compound.compoundWord.segments = null
        allLangEntities[compound.id] = null
    }

    override fun suggestCompound(compoundWord: Word, compound: Compound?): List<Word> {
        var longestPrefix = ""
        if (compound != null) {
            val textSoFar = compound.components.joinToString("") { it.text }
            if (!compoundWord.text.startsWith(textSoFar)) {
                return emptyList()
            }
            longestPrefix = textSoFar
        }

        return allWords(compoundWord.language).filter {
            it != compoundWord && compoundWord.text.startsWith(longestPrefix + it.text) &&
                    it.baseWordLink(this)?.toEntity != compoundWord
        }
    }

    override fun addRule(
        name: String,
        fromLanguage: Language,
        toLanguage: Language,
        logic: RuleLogic,
        addedCategories: String?,
        replacedCategories: String?,
        fromPOS: List<String>,
        toPOS: String?,
        source: List<SourceRef>,
        notes: String?
    ): Rule {
        return Rule(allLangEntities.size, name, fromLanguage, toLanguage,
            logic,
            addedCategories, replacedCategories, fromPOS, toPOS, source, notes
        ).also {
            addRule(it)
        }
    }

    fun addRule(rule: Rule) {
        rules.add(rule)
        allLangEntities.add(rule)
    }

    override fun addParadigm(name: String, language: Language, pos: List<String>): Paradigm {
        return Paradigm(paradigms.size, name, language, pos).also {
            paradigms += it
            paradigmsByLanguage.getOrPut(language) { mutableListOf() }.add(it)
        }
    }

    override fun deleteParadigm(paradigm: Paradigm) {
        paradigms[paradigm.id] = null
        paradigmsByLanguage[paradigm.language]?.remove(paradigm)
    }

    override fun allParadigms(): List<Paradigm> {
        return paradigms.filterNotNull()
    }

    override fun paradigmsForLanguage(lang: Language): List<Paradigm> {
        return paradigmsByLanguage[lang] ?: emptyList()
    }

    override fun paradigmById(id: Int): Paradigm? {
        return paradigms.getOrNull(id)
    }

    override fun paradigmForRule(rule: Rule): Paradigm? {
        return paradigmsForLanguage(rule.fromLanguage).find { rule in it.allRules }
    }

    override fun findRuleExamples(rule: Rule): List<Link> {
        return linksFrom.values.flatten().filter { rule in it.rules }
    }

    override fun allRules(): Iterable<Rule> {
        return rules
    }

    override fun ruleById(id: Int): Rule? {
        return allLangEntities.getOrNull(id) as? Rule
    }

    override fun ruleByName(ruleName: String): Rule? {
        return rules.find { it.name == ruleName }
    }

    override fun allPublications(): List<Publication> {
        return publications.filterNotNull()
    }

    override fun publicationById(id: Int): Publication? {
        return publications.getOrNull(id)
    }

    override fun publicationByRefId(refId: String): Publication? {
        return publications.find { it?.refId == refId }
    }

    override fun addPublication(name: String, refId: String): Publication {
        val publication = Publication(publications.size, name, null, null, null, refId)
        publications.add(publication)
        return publication
    }

    companion object {
        val EMPTY = InMemoryGraphRepository()
    }
}

val diacriticPattern = Regex("\\p{M}")

fun String.removeDiacritics() =
    diacriticPattern.replace(Normalizer.normalize(this, Normalizer.Form.NFKD), "")
