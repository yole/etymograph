package ru.yole.etymograph

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Serializable
data class LanguageData(val name: String, val shortName: String, val reconstructed: Boolean = false)

@Serializable
data class SourceRefData(val pubId: Int? = null, val refText: String)

@Serializable
data class WordData(
    val id: Int,
    val text: String,
    val gloss: String? = null,
    val fullGloss: String? = null,
    val pos: String? = null,
    val classes: List<String>? = null,
    val reconstructed: Boolean = false,
    val sourceRefs: List<SourceRefData>? = null,
    val notes: String? = null,
    val stress: Int? = null
)

@Serializable
data class PhonemeData(
    val id: Int,
    val graphemes: List<String>,
    val sound: String? = null,
    val classes: List<String> = emptyList(),
    val historical: Boolean = false,
    val sourceRefs: List<SourceRefData>? = null,
    val notes: String? = null
)

@Serializable
data class LanguageDetailsData(
    val phonemes: List<PhonemeData>,
    val diphthongs: List<String> = emptyList(),
    val protoLanguageShortName: String? = null,
    val stressRuleId: Int? = null,
    val phonotacticsRuleId: Int? = null,
    val pronunciationRuleId: Int? = null,
    val orthographyRuleId: Int? = null,
    val syllableStructures: List<String> = emptyList(),
    val pos: List<WordCategoryValueData> = emptyList(),
    val grammaticalCategories: List<WordCategoryData> = emptyList(),
    val wordClasses: List<WordCategoryData> = emptyList(),
    val dictionarySettings: String? = null,
    val accentTypes: List<AccentType> = emptyList()
)

@Serializable
sealed class RuleConditionData {
    abstract fun toRuntimeFormat(result: GraphRepository, fromLanguage: Language): RuleCondition
}

private fun requiredPhonemeClassByName(language: Language, phonemeClassName: String?): PhonemeClass? =
    phonemeClassName?.let { className ->
        language.phonemeClassByName(className)
            ?: throw IllegalStateException("Can't find phoneme class referenced in rule: $phonemeClassName")
    }

private fun Language.loadPhonemePattern(phonemeClassName: String?, literal: String?): PhonemePattern =
    PhonemePattern(requiredPhonemeClassByName(this, phonemeClassName), literal)

@Serializable
@SerialName("leaf")
data class LeafRuleConditionData(
    @SerialName("cond") val type: ConditionType,
    @SerialName("cls") val phonemeClassName: String? = null,
    val characters: String? = null,
    val negated: Boolean = false,
    val baseLanguageShortName: String? = null
) : RuleConditionData() {
    override fun toRuntimeFormat(result: GraphRepository, fromLanguage: Language): RuleCondition {
        val phonemeClass = requiredPhonemeClassByName(fromLanguage, phonemeClassName)
        return LeafRuleCondition(type, phonemeClass, characters, negated, baseLanguageShortName)
    }
}

@Serializable
@SerialName("syllable")
class SyllableRuleConditionData(
    val matchType: SyllableMatchType,
    val index: Int,
    @SerialName("cls") val phonemeClassName: String? = null,
    val parameter: String? = null
) : RuleConditionData() {
    override fun toRuntimeFormat(result: GraphRepository, fromLanguage: Language): RuleCondition {
        return SyllableRuleCondition(
            matchType,
            index,
            fromLanguage.loadPhonemePattern(phonemeClassName, parameter)
        )
    }
}

@Serializable
@SerialName("syllableCount")
class SyllableCountConditionData(
    val condition: String? = null,
    val negated: Boolean = false,
    val expectCount: Int
) : RuleConditionData() {
    override fun toRuntimeFormat(result: GraphRepository, fromLanguage: Language): RuleCondition {
        return SyllableCountRuleCondition(condition, negated, expectCount)
    }
}

@Serializable
@SerialName("syllableIndex")
class RelativeSyllableConditionData(
    val matchIndex: Int? = null,
    val matchClass: String? = null,
    val relativeIndex: Int? = null,
    val negated: Boolean = false
) : RuleConditionData() {
    override fun toRuntimeFormat(result: GraphRepository, fromLanguage: Language): RuleCondition {
        return RelativeSyllableRuleCondition(matchIndex, matchClass, relativeIndex, negated)
    }
}

@Serializable
@SerialName("relPhoneme")
class RelativePhonemeRuleConditionData(
    val relativeIndex: Int? = null,
    val negated: Boolean = false,
    @SerialName("tcls") val targetPhonemeClassName: String? = null,
    @SerialName("cls") val matchPhonemeClassName: String? = null,
    val parameter: String? = null,
    val relative: Boolean = true,
    val baseLanguageShortName: String? = null
): RuleConditionData() {
    override fun toRuntimeFormat(result: GraphRepository, fromLanguage: Language): RuleCondition {
        val targetPhonemeClass = requiredPhonemeClassByName(fromLanguage, targetPhonemeClassName) ?: PhonemeClass.sound
        return RelativePhonemeRuleCondition(
            negated,
            relativeIndex?.let { SeekTarget(it, targetPhonemeClass, null, relative) },
            fromLanguage.loadPhonemePattern(matchPhonemeClassName, parameter),
            baseLanguageShortName
        )
    }
}

@Serializable
@SerialName("phonemeEquals")
class PhonemeEqualsRuleConditionData(
    val index: Int,
    @SerialName("cls") val phonemeClassName: String?,
    val relative: Boolean,
    val matchIndex: Int? = null,
    @SerialName("mcls") val matchPhonemeClassName: String? = null,
    val matchRelative: Boolean? = null,
    val negated: Boolean = false
) : RuleConditionData() {
    override fun toRuntimeFormat(result: GraphRepository, fromLanguage: Language): RuleCondition {
        return PhonemeEqualsRuleCondition(
            SeekTarget(index, phonemeClassName?.let { fromLanguage.phonemeClassByName(it) } ?: PhonemeClass.sound, null,relative),
            matchIndex?.let {
                SeekTarget(it, matchPhonemeClassName?.let { fromLanguage.phonemeClassByName(it) } ?: PhonemeClass.sound, null, matchRelative == true)
            },
            negated
        )
    }
}

@Serializable
@SerialName("wordIs")
class WordClassConditionData(
    val wordClass: String,
    val negated: Boolean = false
) : RuleConditionData() {
    override fun toRuntimeFormat(
        result: GraphRepository,
        fromLanguage: Language
    ): RuleCondition {
        return WordClassCondition(wordClass, negated)
    }
}

@Serializable
@SerialName("or")
data class OrRuleConditionData(
    val members: List<RuleConditionData>
) : RuleConditionData() {
    override fun toRuntimeFormat(result: GraphRepository, fromLanguage: Language): RuleCondition =
        OrRuleCondition(members.map { it.toRuntimeFormat(result, fromLanguage) })
}

@Serializable
@SerialName("and")
data class AndRuleConditionData(
    val members: List<RuleConditionData>
) : RuleConditionData() {
    override fun toRuntimeFormat(result: GraphRepository, fromLanguage: Language): RuleCondition =
        AndRuleCondition(members.map { it.toRuntimeFormat(result, fromLanguage) })
}

@Serializable
@SerialName("not")
class NotRuleConditionData(val arg: RuleConditionData) : RuleConditionData() {
    override fun toRuntimeFormat(result: GraphRepository, fromLanguage: Language): RuleCondition =
        NotRuleCondition(arg.toRuntimeFormat(result, fromLanguage))
}

@Serializable
@SerialName("otherwise")
class OtherwiseConditionData : RuleConditionData() {
    override fun toRuntimeFormat(result: GraphRepository, fromLanguage: Language): RuleCondition = OtherwiseCondition
}

@Serializable
data class RuleInstructionData(
    val type: InstructionType,
    val condition: RuleConditionData? = null,
    val args: Array<String>,
    val comment: String? = null
)

@Serializable
data class RuleBranchData(
    val instructions: List<RuleInstructionData>,
    val condition: RuleConditionData? = null,
    val comment: String? = null
)

@Serializable
data class RuleData(
    val id: Int,
    val name: String?,
    @SerialName("fromLang") val fromLanguageShortName: String,
    @SerialName("toLang") val toLanguageShortName: String,
    val branches: List<RuleBranchData>,
    val addedCategories: String? = null,
    val replacedCategories: String? = null,
    val fromPOS: List<String>? = null,
    val toPOS: String? = null,
    val sourceRefs: List<SourceRefData>? = null,
    val notes: String? = null,
    val preInstructions: List<RuleInstructionData>? = null,
    val postInstructions: List<RuleInstructionData>? = null
)

@Serializable
data class RuleSequenceStepData(
    val ruleId: Int,
    val alternativeRuleId: Int? = null,
    val optional: Boolean = false,
    val dispreferred: Boolean = false
)

@Serializable
data class RuleSequenceData(
    val id: Int,
    val name: String,
    @SerialName("fromLang") val fromLanguageShortName: String,
    @SerialName("toLang") val toLanguageShortName: String,
    val steps: List<RuleSequenceStepData>,
    val sourceRefs: List<SourceRefData>? = null,
    val notes: String? = null,
)

@Serializable
data class LinkData(
    @SerialName("from") val fromWordId: Int,
    @SerialName("to") val toWordId: Int,
    val type: String,
    val ruleIds: List<Int>? = null,
    val sequenceId: Int? = null,
    val sourceRefs: List<SourceRefData>? = null,
    val notes: String? = null
)

@Serializable
data class CompoundData(
    val id: Int,
    val compoundId: Int,
    val componentIds: List<Int>,
    val headIndex: Int? = null,
    val sourceRefs: List<SourceRefData>? = null,
    val notes: String? = null
)

@Serializable
data class CorpusTextWordData(
    val index: Int,
    val id: Int,
    val contextGloss: String? = null
)

@Serializable
data class CorpusTextData(
    val id: Int, val text: String, val title: String? = null,
    val words: List<CorpusTextWordData>,
    val sourceRefs: List<SourceRefData>? = null,
    val notes: String? = null,
    val translations: List<TranslationData> = emptyList()
)

@Serializable
data class TranslationData(
    val id: Int,
    val text: String,
    val sourceRefs: List<SourceRefData>? = null,
    val notes: String? = null
)

@Serializable
data class ParadigmCellData(
    val ruleAlternatives: List<Int?>? = null
)

@Serializable
data class ParadigmColumnData(
    val title: String,
    val cells: List<ParadigmCellData>
)

@Serializable
data class ParadigmData(
    val id: Int,
    val name: String,
    val rows: List<String>,
    val columns: List<ParadigmColumnData>,
    val posList: List<String>,
    val preRule: Int? = null,
    val postRule: Int? = null
)

@Serializable
data class PublicationData(
    val id: Int,
    val name: String,
    val refId: String,
    val author: String? = null,
    val date: String? = null,
    val publisher: String? = null
)

@Serializable
data class WordCategoryValueData(
    val name: String,
    val abbreviation: String
)

@Serializable
data class WordCategoryData(
    val name: String,
    val pos: List<String>,
    val values: List<WordCategoryValueData>
)

@Serializable
data class GraphRepositoryData(
    val id: String,
    val name: String,
    val languages: List<LanguageData>,
    val links: List<LinkData>
)

class JsonGraphRepository(val path: Path?) : InMemoryGraphRepository() {
    private val allLinks = mutableListOf<Link>()

    private var _id: String = ""
    private var _name: String = ""

    override val id: String
        get() = _id

    override val name: String
        get() = _name

    override fun createLink(
        fromEntity: LangEntity,
        toEntity: LangEntity,
        type: LinkType,
        rules: List<Rule>,
        sequence: RuleSequence?,
        source: List<SourceRef>,
        notes: String?
    ): Link {
        return super.createLink(fromEntity, toEntity, type, rules, sequence, source, notes).also {
            allLinks.add(it)
        }
    }

    override fun deleteLink(fromEntity: LangEntity, toEntity: LangEntity, type: LinkType): Boolean {
        return super.deleteLink(fromEntity, toEntity, type).also {
            allLinks.removeIf { it.fromEntity == fromEntity && it.toEntity == toEntity && it.type == type }
        }
    }

    override fun save() {
        if (path == null) {
            throw IllegalStateException("Can't save: path not specified")
        }
        saveToJson { relativePath, content ->
            path.resolve(relativePath).createParentDirectories().writeText(content)
        }
    }

    fun saveToJson(consumer: (String, String) -> Unit) {
        val repoData = createGraphRepositoryData()
        consumer("graph.json", theJson.encodeToString(repoData))
        consumer("publications.json", theJson.encodeToString(
            publications.filterNotNull().map {
                PublicationData(it.id, it.name, it.refId, it.author, it.date, it.publisher)
            })
        )
        saveLanguageDetails(consumer)
        saveWords(consumer)
        saveRules(consumer)
        saveCorpus(consumer)
        saveCompounds(consumer)
        saveParadigms(consumer)
        saveRuleSequences(consumer)
    }

    private fun saveLanguageDetails(consumer: (String, String) -> Unit) {
        for (lang in languages.values) {
            val phonemes = lang.phonemes.map {
                PhonemeData(
                    it.id, it.graphemes, it.sound, it.classes.toList(), it.historical,
                    it.source.sourceToSerializedFormat(), it.notes
                )
            }
            val languageDetailsData = LanguageDetailsData(
                phonemes,
                lang.diphthongs,
                lang.protoLanguage?.shortName,
                lang.stressRule?.resolve()?.id,
                lang.phonotacticsRule?.resolve()?.id,
                lang.pronunciationRule?.resolve()?.id,
                lang.orthographyRule?.resolve()?.id,
                lang.syllableStructures,
                serializeWordCategoryValues(lang.pos),
                serializeWordCategories(lang.grammaticalCategories),
                serializeWordCategories(lang.wordClasses),
                lang.dictionarySettings,
                lang.accentTypes.toList()
            )
            consumer(lang.shortName + "/language.json", theJson.encodeToString(languageDetailsData))
        }
    }

    private fun serializeWordCategories(categories: List<WordCategory>) =
        categories.map {
            WordCategoryData(it.name, it.pos, serializeWordCategoryValues(it.values))
        }

    private fun serializeWordCategoryValues(values: List<WordCategoryValue>) =
        values.map { v -> WordCategoryValueData(v.name, v.abbreviation) }

    private fun saveWords(consumer: (String, String) -> Unit) {
        for (language in languages.values) {
            val wordData = allLangEntities.filterIsInstance<Word>().filter { it.language == language }.map {
                WordData(it.id, it.text, it.gloss, it.fullGloss, it.pos,
                    it.classes.takeIf { it.isNotEmpty() },
                    it.reconstructed,
                    it.source.sourceToSerializedFormat(), it.notes,
                    if (it.explicitStress) it.stressedPhonemeIndex else null
                )
            }
            consumer(language.shortName + "/words.json", theJson.encodeToString(wordData))
        }
    }

    private fun saveRules(consumer: (String, String) -> Unit) {
        for (language in languages.values) {
            val ruleData = rules.filter { it.toLanguage == language }.map { it.ruleToSerializedFormat() }
            if (ruleData.isNotEmpty()) {
                consumer("${language.shortName}/rules.json", theJson.encodeToString(ruleData))
            }
        }
    }

    private fun saveCorpus(consumer: (String, String) -> Unit) {
        for (language in languages.values) {
            val corpusData = corpus.filter { it.language == language}.map {
                CorpusTextData(
                    it.id, it.text, it.title,
                    it.words.map { wa -> CorpusTextWordData(wa.index, wa.word.id, wa.contextGloss) },
                    it.source.sourceToSerializedFormat(), it.notes,
                    collectTranslations(it)
                )
            }
            if (corpusData.isNotEmpty()) {
                consumer(language.shortName + "/corpus.json", theJson.encodeToString(corpusData))
            }
        }
    }

    private fun collectTranslations(corpusText: CorpusText): List<TranslationData> {
        return allLangEntities.filterIsInstance<Translation>().filter { it.corpusText == corpusText }.map { t ->
            TranslationData(t.id, t.text, t.source.sourceToSerializedFormat(), t.notes)
        }
    }

    private fun saveCompounds(consumer: (String, String) -> Unit) {
        for (language in languages.values) {
            val compoundData = allLangEntities.filterIsInstance<Compound>()
                .filter { it.compoundWord.language == language }
                .map { c ->
                    CompoundData(c.id, c.compoundWord.id, c.components.map { it.id }, c.headIndex, c.source.sourceToSerializedFormat(), c.notes)
                }
            if (compoundData.isNotEmpty()) {
                consumer("${language.shortName}/compounds.json", theJson.encodeToString(compoundData))
            }
        }
    }

    private fun saveParadigms(consumer: (String, String) -> Unit) {
        for (language in languages.values) {
            val paradigmData = paradigms.filterNotNull().filter { it.language == language }.map {
                ParadigmData(it.id, it.name, it.rowTitles, it.columns.map { col ->
                    ParadigmColumnData(col.title, col.cells.map { cell ->
                        ParadigmCellData(ruleAlternatives = cell?.ruleAlternatives?.map { r -> r?.id })
                    })
                }, it.pos, it.preRule?.id, it.postRule?.id)
            }
            if (paradigmData.isNotEmpty()) {
                consumer("${language.shortName}/paradigms.json", theJson.encodeToString(paradigmData))
            }
        }
    }

    private fun saveRuleSequences(consumer: (String, String) -> Unit) {
        for (language in languages.values) {
            val ruleSequenceData = allLangEntities.filterIsInstance<RuleSequence>().filter { it.toLanguage == language }.map { s ->
                RuleSequenceData(
                    s.id, s.name, s.fromLanguage.shortName, s.toLanguage.shortName,
                    s.steps.map { RuleSequenceStepData(it.ruleId, it.alternativeRuleId, it.optional, it.dispreferred) },
                    s.source.sourceToSerializedFormat(), s.notes)
            }
            if (ruleSequenceData.isNotEmpty()) {
                consumer("${language.shortName}/ruleSequences.json", theJson.encodeToString(ruleSequenceData))
            }
        }
    }

    private fun createGraphRepositoryData(): GraphRepositoryData {
        return GraphRepositoryData(
            id,
            name,
            languages.values.map { LanguageData(it.name, it.shortName, it.reconstructed) },
            allLinks.map { link ->
                LinkData(
                    link.fromEntity.id, link.toEntity.id,
                    link.type.id, link.rules.takeIf { it.isNotEmpty() }?.map { it.id },
                    link.sequence?.id,
                    link.source.sourceToSerializedFormat(),
                    link.notes
                )
            }
        )
    }

    private fun loadJson(contentProviderCallback: (String) -> String?) {
        val graphJsonContent = contentProviderCallback("graph.json")
            ?: throw IllegalStateException("Cannot load 'graph.json'")
        val data = Json.decodeFromString<GraphRepositoryData>(graphJsonContent)
        _id = data.id
        _name = data.name
        for (languageData in data.languages) {
            val language = Language(languageData.name, languageData.shortName)
            language.reconstructed = languageData.reconstructed
            addLanguage(language)
        }
        loadLanguageDetails(contentProviderCallback)
        loadPublications(contentProviderCallback)
        loadWords(contentProviderCallback)
        loadRules(contentProviderCallback)
        loadCorpus(contentProviderCallback)
        loadCompounds(contentProviderCallback)
        loadParadigms(contentProviderCallback)
        loadRuleSequences(contentProviderCallback)

        for (link in data.links) {
            val fromWord = allLangEntities[link.fromWordId]
            val toWord = allLangEntities[link.toWordId]
            if (fromWord != null && toWord != null) {
                try {
                    addLink(
                        fromWord,
                        toWord,
                        Link.allLinkTypes.first { it.id == link.type },
                        link.ruleIds
                            ?.takeIf { it.isNotEmpty() }
                            ?.map {
                                allLangEntities[it] as? Rule ?: throw IllegalStateException("Broken rule ID reference $it")
                            }
                            ?: emptyList(),
                        link.sequenceId?.let { allLangEntities[it] as RuleSequence },
                        loadSource(link.sourceRefs),
                        link.notes
                    )
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException("Cycle when loading links: from entity ${link.fromWordId}, to entity ${link.toWordId}")
                }
            }
        }
    }

    private fun loadLanguageDetails(contentProviderCallback: (String) -> String?) {
        val protoShortByLanguage = mutableMapOf<Language, String>()
        for (language in languages.values) {
            val content = contentProviderCallback(language.shortName + "/language.json")
                ?: throw IllegalStateException("Can't find language details file for language ${language.shortName}")
            val data = Json.decodeFromString<LanguageDetailsData>(content)
            for (phonemeData in data.phonemes) {
                val phoneme = Phoneme(
                    phonemeData.id,
                    phonemeData.graphemes,
                    phonemeData.sound,
                    phonemeData.classes.toSet(),
                    phonemeData.historical,
                    loadSource(phonemeData.sourceRefs),
                    phonemeData.notes
                )
                language.phonemes += phoneme
                if (phoneme.id != -1) {
                    setLangEntity(phoneme.id, phoneme)
                }
            }
            language.diphthongs = data.diphthongs
            data.protoLanguageShortName?.let { protoShortByLanguage[language] = it }
            language.stressRule = data.stressRuleId?.let { ruleRef(this, it) }
            language.phonotacticsRule = data.phonotacticsRuleId?.let { ruleRef(this, it) }
            language.pronunciationRule = data.pronunciationRuleId?.let { ruleRef(this, it) }
            language.orthographyRule = data.orthographyRuleId?.let { ruleRef(this, it) }
            language.syllableStructures = data.syllableStructures
            language.pos = deserializeWordCategoryValues(data.pos)
            language.grammaticalCategories = deserializeWordCategories(data.grammaticalCategories)
            language.wordClasses = deserializeWordCategories(data.wordClasses)
            language.dictionarySettings = data.dictionarySettings
            language.accentTypes = data.accentTypes.toSet()
        }
        for ((lang, protoShort) in protoShortByLanguage) {
            lang.protoLanguage = languageByShortName(protoShort)
        }
    }

    private fun loadWords(contentProviderCallback: (String) -> String?) {
        for (language in languages.values) {
            val data = Json.decodeFromString<List<WordData>>(contentProviderCallback(language.shortName + "/words.json")!!)
            for (wordData in data) {
                val wordsByText = mapOfWordsByText(language, wordData.text)
                val word = Word(
                    wordData.id, wordData.text, language, wordData.gloss,
                    wordData.fullGloss,
                    wordData.pos,
                    wordData.classes ?: emptyList(),
                    wordData.reconstructed,
                    loadSource(wordData.sourceRefs),
                    wordData.notes
                )
                if (wordData.stress != null) {
                    word.stressedPhonemeIndex = wordData.stress
                    word.explicitStress = true
                }
                setLangEntity(word.id, word)
                wordsByText.add(word)
            }
        }
    }

    private fun deserializeWordCategories(data: List<WordCategoryData>): MutableList<WordCategory> {
        return data.mapTo(mutableListOf()) {
            WordCategory(it.name, it.pos, deserializeWordCategoryValues(it.values))
        }
    }

    private fun deserializeWordCategoryValues(values: List<WordCategoryValueData>): MutableList<WordCategoryValue> =
        values.mapTo(mutableListOf()) { dv -> WordCategoryValue(dv.name, dv.abbreviation) }

    private fun loadRules(contentProviderCallback: (String) -> String?) {
        for (language in languages.values) {
            val ruleJson = contentProviderCallback(language.shortName + "/rules.json") ?: continue
            val data = Json.decodeFromString<List<RuleData>>(ruleJson)
            for (rule in data) {
                val fromLanguage = languageByShortName(rule.fromLanguageShortName)!!
                val toLanguage = languageByShortName(rule.toLanguageShortName)!!
                val addedRule = ruleFromSerializedFormat(rule, fromLanguage, toLanguage)
                rules.add(addedRule)
                setLangEntity(rule.id, addedRule)
            }
        }
    }

    fun ruleFromSerializedFormat(
        rule: RuleData,
        fromLanguage: Language,
        toLanguage: Language
    ): Rule {
        val logic = if (rule.branches.singleOrNull()?.instructions?.any { it.type == InstructionType.Spe } == true) {
            SpeRuleLogic(
                ruleInstructionsFromSerializedFormat(this, fromLanguage, toLanguage,
                    rule.branches[0].instructions,
                    rule.branches[0].comment
                ) as List<SpeInstruction>,
                rule.postInstructions?.let {
                    ruleInstructionsFromSerializedFormat(this, fromLanguage, toLanguage, it)
                } ?: emptyList()
            )
        }
        else {
            MorphoRuleLogic(
                rule.preInstructions?.let {
                    ruleInstructionsFromSerializedFormat(this, fromLanguage, toLanguage, it)
                } ?: emptyList(),
                ruleBranchesFromSerializedFormat(this, fromLanguage, toLanguage, rule.branches),
                rule.postInstructions?.let {
                    ruleInstructionsFromSerializedFormat(this, fromLanguage, toLanguage, it)
                } ?: emptyList()
            )
        }

        return Rule(
            rule.id,
            rule.name ?: "",
            fromLanguage,
            languageByShortName(rule.toLanguageShortName)!!,
            logic,
            rule.addedCategories,
            rule.replacedCategories,
            rule.fromPOS ?: emptyList(),
            rule.toPOS,
            loadSource(rule.sourceRefs),
            rule.notes
        )
    }

    private fun loadCorpus(contentProviderCallback: (String) -> String?) {
        for (language in languages.values) {
            val corpusJson = contentProviderCallback(language.shortName + "/corpus.json") ?: continue
            val corpusTextList = Json.decodeFromString<List<CorpusTextData>>(corpusJson)
            for (corpusText in corpusTextList) {
                val addedCorpusText = CorpusText(
                    corpusText.id,
                    corpusText.text,
                    corpusText.title,
                    language,
                    corpusText.words.map { CorpusWordAssociation(it.index, allLangEntities[it.id] as Word, it.contextGloss) },
                    loadSource(corpusText.sourceRefs),
                    corpusText.notes
                )
                corpus += addedCorpusText
                setLangEntity(corpusText.id, addedCorpusText)

                for (translationData in corpusText.translations) {
                    val translation = Translation(
                        translationData.id,
                        addedCorpusText,
                        translationData.text,
                        loadSource(translationData.sourceRefs),
                        translationData.notes
                    )
                    setLangEntity(translation.id, translation)
                    storeTranslation(addedCorpusText, translation)
                }
            }
        }
    }

    private fun loadCompounds(contentProviderCallback: (String) -> String?) {
        for (language in languages.values) {
            val compoundJson = contentProviderCallback(language.shortName + "/compounds.json") ?: continue
            val compoundList = Json.decodeFromString<List<CompoundData>>(compoundJson)
            for (compoundData in compoundList) {
                val compoundWord = allLangEntities[compoundData.compoundId] as? Word
                    ?: throw IllegalStateException("No word with ID ${compoundData.compoundId}")
                val componentWords = compoundData.componentIds.mapTo(ArrayList()) { allLangEntities[it] as Word }
                val compound = Compound(
                    compoundData.id,
                    compoundWord,
                    componentWords,
                    compoundData.headIndex,
                    loadSource(compoundData.sourceRefs),
                    compoundData.notes
                )
                compounds.getOrPut(compoundWord.id) { arrayListOf() }.add(compound)
                setLangEntity(compound.id, compound)
            }
        }
    }

    private fun loadParadigms(contentProviderCallback: (String) -> String?) {
        for (language in languages.values) {
            val paradigmJson = contentProviderCallback(language.shortName + "/paradigms.json") ?: continue
            val paradigmList = Json.decodeFromString<List<ParadigmData>>(paradigmJson)

            for (paradigmData in paradigmList) {
                while (paradigmData.id >= paradigms.size) {
                    paradigms.add(null)
                }

                val paradigm = Paradigm(paradigmData.id, paradigmData.name, language, paradigmData.posList)
                paradigms[paradigmData.id] = paradigm
                paradigmsByLanguage.getOrPut(language) { mutableListOf() }.add(paradigm)
                paradigm.preRule = paradigmData.preRule?.let { ruleById(it) }
                paradigm.postRule = paradigmData.postRule?.let { ruleById(it) }
                paradigm.apply {
                    for (row in paradigmData.rows) {
                        addRow(row)
                    }
                    for ((colIndex, column) in paradigmData.columns.withIndex()) {
                        addColumn(column.title)
                        for ((rowIndex, cell) in column.cells.withIndex()) {
                            val alternatives = cell.ruleAlternatives
                            if (alternatives != null) {
                                val rules = alternatives.map { id -> id?.let { ruleById(it) } }
                                setRule(rowIndex, colIndex, rules)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun loadRuleSequences(contentProviderCallback: (String) -> String?) {
        for (language in languages.values) {
            val ruleSequenceJson = contentProviderCallback(language.shortName + "/ruleSequences.json") ?: continue
            val ruleSequenceList = Json.decodeFromString<List<RuleSequenceData>>(ruleSequenceJson)

            for (sequence in ruleSequenceList) {
                val ruleSequence = RuleSequence(
                    sequence.id,
                    sequence.name,
                    languageByShortName(sequence.fromLanguageShortName)
                        ?: throw IllegalStateException("Broken language ID reference ${sequence.fromLanguageShortName}"),
                    languageByShortName(sequence.toLanguageShortName)!!,
                    sequence.steps.map { RuleSequenceStepRef(it.ruleId, it.alternativeRuleId, it.optional, it.dispreferred )},
                    loadSource(sequence.sourceRefs),
                    sequence.notes
                )
                setLangEntity(ruleSequence.id, ruleSequence)
            }
        }
    }

    private fun loadPublications(contentProviderCallback: (String) -> String?) {
        val publicationsData = Json.decodeFromString<List<PublicationData>>(contentProviderCallback("publications.json")!!)
        for (pubData in publicationsData) {
            while (pubData.id > publications.size) {
                publications.add(null)
            }
            publications.add(Publication(pubData.id, pubData.name, pubData.author, pubData.date, pubData.publisher, pubData.refId))
        }
    }

    private fun setLangEntity(id: Int, entity: LangEntity) {
        while (allLangEntities.size <= id) {
            allLangEntities.add(null)
        }
        if (allLangEntities[id] != null) {
            throw IllegalStateException("Duplicate ID $id")
        }
        allLangEntities[id] = entity
    }

    companion object {
        val theJson = Json { prettyPrint = true }

        fun fromJson(path: Path): JsonGraphRepository {
            val result = JsonGraphRepository(path)
            result.loadJson {
                val filePath = path.resolve(it)
                if (filePath.exists()) filePath.readText() else null
            }
            return result
        }

        fun fromJsonProvider(jsonProvider: (String) -> String?): JsonGraphRepository {
            val result = JsonGraphRepository(null)
            result.loadJson(jsonProvider)
            return result
        }

        private fun List<SourceRef>.sourceToSerializedFormat(): List<SourceRefData>? {
            return map { SourceRefData(it.pubId, it.refText) }.takeIf { it.isNotEmpty() }
        }

        private fun loadSource(sourceRefs: List<SourceRefData>?): List<SourceRef> {
            return sourceRefs?.map { SourceRef(it.pubId, it.refText) } ?: emptyList()
        }

        fun Rule.ruleToSerializedFormat(): RuleData {
            val logic = this.logic
            val logicData = when (logic) {
                is MorphoRuleLogic ->
                    logic.branches.map { branch ->
                        RuleBranchData(
                            branch.instructions.toSerializedFormat(),
                            branch.condition.toSerializedFormat(),
                            branch.comment
                        )
                    }
                is SpeRuleLogic ->
                    listOf(RuleBranchData(
                        logic.instructions.toSerializedFormat(),
                        OtherwiseConditionData(),
                        null
                    ))
            }
            return RuleData(
                id,
                name,
                fromLanguage.shortName,
                toLanguage.shortName,
                logicData,
                addedCategories,
                replacedCategories,
                fromPOS.takeIf { it.isNotEmpty() },
                toPOS,
                source.sourceToSerializedFormat(),
                notes.takeIf { !it.isNullOrEmpty() },
                preInstructions = (logic as? MorphoRuleLogic)?.preInstructions?.toSerializedFormat()?.takeIf { it.isNotEmpty() },
                postInstructions = logic.postInstructions.toSerializedFormat().takeIf { it.isNotEmpty() }
            )
        }

        private fun List<RuleInstruction>.toSerializedFormat(): List<RuleInstructionData> {
            return map { it.toSerializedFormat() }
        }

        fun RuleInstruction.toSerializedFormat() =
            RuleInstructionData(
                type,
                condition = if (this is SpeInstruction) condition?.toSerializedFormat() else null,
                args = argsToSerializedFormat(),
                comment = comment
            )

        private fun RuleInstruction.argsToSerializedFormat(): Array<String> =
            when (this) {
                is ApplyRuleInstruction -> arrayOf(
                    ruleRef.resolve().id.toString()
                )
                is ApplySoundRuleInstruction -> {
                    val ruleId = ruleRef.resolve().id.toString()
                    seekTarget?.let {
                        arrayOf(ruleId, it.toEditableText())
                    } ?: arrayOf(ruleId)
                }
                is InsertInstruction -> arrayOf(
                    arg,
                    relIndex.toString(),
                    seekTarget.toEditableText()
                )
                else -> if (type.takesArgument) arrayOf(arg) else emptyArray()
            }

        private fun RuleCondition.toSerializedFormat(): RuleConditionData = when(this) {
            is SyllableRuleCondition -> SyllableRuleConditionData(
                matchType,
                index,
                phonemePattern.phonemeClass?.name,
                phonemePattern.literal
            )
            is SyllableCountRuleCondition -> SyllableCountConditionData(
                condition, negated, expectCount
            )
            is RelativeSyllableRuleCondition -> RelativeSyllableConditionData(
                matchIndex, matchClass, relativeIndex, negated
            )
            is RelativePhonemeRuleCondition -> RelativePhonemeRuleConditionData(
                seekTarget?.index,
                negated,
                seekTarget?.phonemeClass?.name,
                phonemePattern.phonemeClass?.name,
                phonemePattern.literal,
                seekTarget?.relative ?: false,
                baseLanguageShortName
            )
            is PhonemeEqualsRuleCondition -> PhonemeEqualsRuleConditionData(
                target.index,
                target.phonemeClass?.name,
                target.relative,
                matchTarget?.index,
                matchTarget?.phonemeClass?.name,
                matchTarget?.relative,
                negated
            )
            is WordClassCondition -> WordClassConditionData(wordClass, negated)
            is LeafRuleCondition -> LeafRuleConditionData(
                type,
                phonemeClass?.name,
                parameter,
                negated,
                baseLanguageShortName
            )
            is OrRuleCondition -> OrRuleConditionData(members.map { it.toSerializedFormat()} )
            is AndRuleCondition -> AndRuleConditionData(members.map { it.toSerializedFormat()} )
            is NotRuleCondition -> NotRuleConditionData(arg.toSerializedFormat())
            is OtherwiseCondition -> OtherwiseConditionData()
        }

        fun ruleBranchesFromSerializedFormat(
            result: InMemoryGraphRepository,
            fromLanguage: Language,
            toLanguage: Language,
            branches: List<RuleBranchData>
        ): List<RuleBranch> {
            return branches.map { branchData ->
                RuleBranch(
                    branchData.condition!!.toRuntimeFormat(result, fromLanguage),
                    ruleInstructionsFromSerializedFormat(result, fromLanguage, toLanguage, branchData.instructions),
                    branchData.comment
                )
            }
        }

        private fun ruleInstructionsFromSerializedFormat(
            result: InMemoryGraphRepository,
            fromLanguage: Language,
            toLanguage: Language,
            ruleInstructionData: List<RuleInstructionData>,
            firstInstructionComment: String? = null
        ) = ruleInstructionData.mapIndexed { index, insnData ->
            ruleInstructionFromSerializedFormat(result, fromLanguage, toLanguage, insnData, firstInstructionComment?.takeIf { index == 0 } )
        }

        fun ruleInstructionFromSerializedFormat(
            result: GraphRepository,
            fromLanguage: Language,
            toLanguage: Language,
            insnData: RuleInstructionData,
            extraComment: String? = null
        ): RuleInstruction =
            when (insnData.type) {
                InstructionType.ApplyRule ->
                    ApplyRuleInstruction(ruleRef(result, insnData.args[0].toInt()), insnData.comment)
                InstructionType.ApplySoundRule ->
                    ApplySoundRuleInstruction(fromLanguage, ruleRef(result, insnData.args[0].toInt()), insnData.args.getOrNull(1), insnData.comment)
                InstructionType.ApplyStress ->
                    ApplyStressInstruction(fromLanguage, insnData.args[0], insnData.comment)
                InstructionType.Prepend, InstructionType.Append ->
                    PrependAppendInstruction(insnData.type, fromLanguage, insnData.args[0], insnData.comment)
                InstructionType.Insert ->
                    InsertInstruction(insnData.args[0], insnData.args[1].toInt(), SeekTarget.parse(insnData.args[2], fromLanguage), insnData.comment)
                InstructionType.PrependMorpheme, InstructionType.AppendMorpheme ->
                    MorphemeInstruction(insnData.type, insnData.args[0].toInt(), insnData.comment)
                InstructionType.Spe ->
                    SpeInstruction(SpePattern.parse(fromLanguage, toLanguage, insnData.args[0]),
                        insnData.condition?.toRuntimeFormat(result, fromLanguage), insnData.comment ?: extraComment)
                else ->
                    RuleInstruction(insnData.type, insnData.args.firstOrNull() ?: "", insnData.comment)
            }

        private fun ruleRef(repo: GraphRepository, ruleId: Int) =
            RuleRef { repo.ruleById(ruleId) ?: throw IllegalStateException("Broken rule ID reference $ruleId") }
    }
}

fun main() {
    val ieRepo = JsonGraphRepository.fromJson(Path.of("data/ie"))
    ieRepo.save()

    val jrrtRepo = JsonGraphRepository.fromJson(Path.of("data/jrrt"))
    jrrtRepo.save()
}
