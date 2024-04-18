package ru.yole.etymograph

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
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
    @SerialName("lang") val languageShortName: String,
    val gloss: String? = null,
    val fullGloss: String? = null,
    val pos: String? = null,
    val classes: List<String>? = null,
    val reconstructed: Boolean = false,
    val sourceRefs: List<SourceRefData>? = null,
    val notes: String? = null
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
    val diphthongs: List<String>,
    val stressRuleId: Int? = null,
    val phonotacticsRuleId: Int? = null,
    val orthographyRuleId: Int? = null
)

@Serializable
data class SyllableStructureData(@SerialName("lang") val languageShortName: String, val syllableStructures: List<String>)

@Serializable
sealed class RuleConditionData {
    abstract fun toRuntimeFormat(result: InMemoryGraphRepository, fromLanguage: Language): RuleCondition
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
    override fun toRuntimeFormat(result: InMemoryGraphRepository, fromLanguage: Language): RuleCondition {
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
    override fun toRuntimeFormat(result: InMemoryGraphRepository, fromLanguage: Language): RuleCondition {
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
    override fun toRuntimeFormat(result: InMemoryGraphRepository, fromLanguage: Language): RuleCondition {
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
    override fun toRuntimeFormat(result: InMemoryGraphRepository, fromLanguage: Language): RuleCondition {
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
    override fun toRuntimeFormat(result: InMemoryGraphRepository, fromLanguage: Language): RuleCondition {
        val targetPhonemeClass = requiredPhonemeClassByName(fromLanguage, targetPhonemeClassName)
        return RelativePhonemeRuleCondition(
            negated,
            relativeIndex?.let { SeekTarget(it, targetPhonemeClass, relative) },
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
    val relative: Boolean
) : RuleConditionData() {
    override fun toRuntimeFormat(result: InMemoryGraphRepository, fromLanguage: Language): RuleCondition {
        return PhonemeEqualsRuleCondition(
            SeekTarget(index, phonemeClassName?.let { fromLanguage.phonemeClassByName(it) }, relative)
        )
    }

}

@Serializable
@SerialName("or")
data class OrRuleConditionData(
    val members: List<RuleConditionData>
) : RuleConditionData() {
    override fun toRuntimeFormat(result: InMemoryGraphRepository, fromLanguage: Language): RuleCondition =
        OrRuleCondition(members.map { it.toRuntimeFormat(result, fromLanguage) })
}

@Serializable
@SerialName("and")
data class AndRuleConditionData(
    val members: List<RuleConditionData>
) : RuleConditionData() {
    override fun toRuntimeFormat(result: InMemoryGraphRepository, fromLanguage: Language): RuleCondition =
        AndRuleCondition(members.map { it.toRuntimeFormat(result, fromLanguage) })
}

@Serializable
@SerialName("otherwise")
class OtherwiseConditionData : RuleConditionData() {
    override fun toRuntimeFormat(result: InMemoryGraphRepository, fromLanguage: Language): RuleCondition = OtherwiseCondition
}

@Serializable
data class RuleInstructionData(val type: InstructionType, val args: Array<String>)

@Serializable
data class RuleBranchData(
    val instructions: List<RuleInstructionData>,
    val condition: RuleConditionData? = null
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
    val fromPOS: String? = null,
    val toPOS: String? = null,
    val sourceRefs: List<SourceRefData>? = null,
    val notes: String? = null,
    val preInstructions: List<RuleInstructionData>? = null
)

@Serializable
data class RuleSequenceData(
    val id: Int,
    val name: String,
    @SerialName("fromLang") val fromLanguageShortName: String,
    @SerialName("toLang") val toLanguageShortName: String,
    val ruleIds: List<Int>,
    val sourceRefs: List<SourceRefData>? = null,
    val notes: String? = null,
)

@Serializable
data class LinkData(
    @SerialName("from") val fromWordId: Int,
    @SerialName("to") val toWordId: Int,
    val type: String,
    val ruleIds: List<Int>? = null,
    val sourceRefs: List<SourceRefData>? = null,
    val notes: String? = null
)

@Serializable
data class CompoundData(
    val id: Int,
    val compoundId: Int,
    val componentIds: List<Int>,
    val sourceRefs: List<SourceRefData>? = null,
    val notes: String? = null
)

@Serializable
data class CorpusTextData(
    val id: Int, val text: String, val title: String?,
    @SerialName("lang") val languageShortName: String,
    val wordIds: List<Int?>,
    val sourceRefs: List<SourceRefData>? = null,
    val notes: String? = null
)

@Serializable
data class TranslationData(
    val id: Int,
    val corpusTextId: Int,
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
    @SerialName("lang") val languageShortName: String,
    val rows: List<String>,
    val columns: List<ParadigmColumnData>,
    val posList: List<String>
)

@Serializable
data class PublicationData(
    val id: Int,
    val name: String,
    val refId: String
)

@Serializable
data class WordCategoryValueData(
    val name: String,
    val abbreviation: String
)

@Serializable
data class WordCategoryData(
    @SerialName("lang") val languageShortName: String,
    val name: String,
    val pos: List<String>,
    val values: List<WordCategoryValueData>
)

@Serializable
data class GraphRepositoryData(
    val id: String,
    val name: String,
    val languages: List<LanguageData>,
    val words: List<WordData>,
    val rules: List<RuleData>,
    val links: List<LinkData>,
    val corpusTexts: List<CorpusTextData>,
    val paradigms: List<ParadigmData>,
    val syllableStructures: List<SyllableStructureData>,
    val compounds: List<CompoundData>,
    val translations: List<TranslationData>,
    val grammaticalCategories: List<WordCategoryData>,
    val wordClasses: List<WordCategoryData>,
    val ruleSequences: List<RuleSequenceData>
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
        source: List<SourceRef>,
        notes: String?
    ): Link {
        return super.createLink(fromEntity, toEntity, type, rules, source, notes).also {
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
            publications.filterNotNull().map { PublicationData(it.id, it.name, it.refId) })
        )
        saveLanguageDetails(consumer)
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
                lang.stressRule?.resolve()?.id,
                lang.phonotacticsRule?.resolve()?.id,
                lang.orthographyRule?.resolve()?.id
            )
            consumer(lang.shortName + "/language.json", theJson.encodeToString(languageDetailsData))
        }
    }

    private fun createGraphRepositoryData(): GraphRepositoryData {
        val syllableStructureData = languages.values.mapNotNull { lang ->
            lang.syllableStructures.takeIf { it.isNotEmpty() }?.let { SyllableStructureData(lang.shortName, it) }
        }
        val grammarCategoryData = mapWordCategories { it.grammaticalCategories }
        val wordClassData = mapWordCategories { it.wordClasses }
        return GraphRepositoryData(
            id,
            name,
            languages.values.map { LanguageData(it.name, it.shortName, it.reconstructed) },
            allLangEntities.filterIsInstance<Word>().map {
                WordData(it.id, it.text, it.language.shortName, it.gloss, it.fullGloss, it.pos,
                    it.classes.takeIf { it.isNotEmpty() },
                    it.reconstructed,
                    it.source.sourceToSerializedFormat(), it.notes)
            },
            rules.map { it.ruleToSerializedFormat() },
            allLinks.map { link ->
                LinkData(
                    link.fromEntity.id, link.toEntity.id,
                    link.type.id, link.rules.takeIf { it.isNotEmpty() }?.map { it.id },
                    link.source.sourceToSerializedFormat(),
                    link.notes
                )
            },
            corpus.map {
                CorpusTextData(
                    it.id, it.text, it.title, it.language.shortName,
                    it.words.map { it?.id },
                    it.source.sourceToSerializedFormat(), it.notes
                )
            },
            paradigms.filterNotNull().map {
                ParadigmData(it.id, it.name, it.language.shortName, it.rowTitles, it.columns.map { col ->
                    ParadigmColumnData(col.title, col.cells.map { cell ->
                        ParadigmCellData(ruleAlternatives = cell?.ruleAlternatives?.map { r -> r?.id })
                    })
                }, it.pos)
            },
            syllableStructureData,
            allLangEntities.filterIsInstance<Compound>().map { c ->
                CompoundData(c.id, c.compoundWord.id, c.components.map { it.id }, c.source.sourceToSerializedFormat(), c.notes)
            },
            allLangEntities.filterIsInstance<Translation>().map { t ->
                TranslationData(t.id, t.corpusText.id, t.text, t.source.sourceToSerializedFormat(), t.notes)
            },
            grammarCategoryData,
            wordClassData,
            allLangEntities.filterIsInstance<RuleSequence>().map { s ->
                RuleSequenceData(
                    s.id, s.name, s.fromLanguage.shortName, s.toLanguage.shortName,
                    s.ruleIds,
                    s.source.sourceToSerializedFormat(), s.notes)
            }
        )
    }

    private fun mapWordCategories(prop: (Language) -> List<WordCategory>) = languages.values.flatMap { lang ->
        prop(lang).map {
            WordCategoryData(lang.shortName, it.name, it.pos, it.values.map { v ->
                WordCategoryValueData(v.name, v.abbreviation)
            })
        }
    }

    private fun loadJson(contentProviderCallback: (String) -> String) {
        val data = Json.decodeFromString<GraphRepositoryData>(contentProviderCallback("graph.json"))
        _id = data.id
        _name = data.name
        for (languageData in data.languages) {
            val language = Language(languageData.name, languageData.shortName)
            language.reconstructed = languageData.reconstructed
            addLanguage(language)
        }
        loadLanguageDetails(contentProviderCallback)

        for (syllableStructureData in data.syllableStructures) {
            languageByShortName(syllableStructureData.languageShortName)!!.syllableStructures = syllableStructureData.syllableStructures
        }
        loadPublications(contentProviderCallback)
        for (wordData in data.words) {
            val language = languageByShortName(wordData.languageShortName)!!
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
            setLangEntity(word.id, word)
            wordsByText.add(word)
        }
        for (rule in data.rules) {
            val fromLanguage = languageByShortName(rule.fromLanguageShortName)!!
            val addedRule = Rule(
                rule.id,
                rule.name ?: "",
                fromLanguage,
                languageByShortName(rule.toLanguageShortName)!!,
                RuleLogic(
                    rule.preInstructions?.let {
                        ruleInstructionsFromSerializedFormat(this, fromLanguage, it)
                    } ?: emptyList(),
                    ruleBranchesFromSerializedFormat(this, fromLanguage, rule.branches)
                ),
                rule.addedCategories,
                rule.replacedCategories,
                rule.fromPOS,
                rule.toPOS,
                loadSource(rule.sourceRefs),
                rule.notes
            )
            rules.add(addedRule)
            setLangEntity(rule.id, addedRule)
        }
        for (sequence in data.ruleSequences) {
            val ruleSequence = RuleSequence(
                sequence.id,
                sequence.name,
                languageByShortName(sequence.fromLanguageShortName)
                    ?: throw IllegalStateException("Broken language ID reference ${sequence.fromLanguageShortName}"),
                languageByShortName(sequence.toLanguageShortName)!!,
                sequence.ruleIds,
                loadSource(sequence.sourceRefs),
                sequence.notes
            )
            setLangEntity(ruleSequence.id, ruleSequence)
        }
        for (link in data.links) {
            val fromWord = allLangEntities[link.fromWordId]
            val toWord = allLangEntities[link.toWordId]
            if (fromWord != null && toWord != null) {
                addLink(
                    fromWord,
                    toWord,
                    Link.allLinkTypes.first { it.id == link.type },
                    link.ruleIds
                        ?.takeIf { it.isNotEmpty() }
                        ?.map { allLangEntities[it] as? Rule ?: throw IllegalStateException("Broken rule ID reference $it") }
                        ?: emptyList(),
                    loadSource(link.sourceRefs),
                    link.notes
                )
            }
        }
        for (compoundData in data.compounds) {
            val compoundWord = allLangEntities[compoundData.compoundId] as? Word ?: throw IllegalStateException("No word with ID ${compoundData.compoundId}")
            val componentWords = compoundData.componentIds.mapTo(ArrayList()) { allLangEntities[it] as Word }
            val compound = Compound(compoundData.id, compoundWord, componentWords, loadSource(compoundData.sourceRefs), compoundData.notes)
            compounds.getOrPut(compoundWord.id) { arrayListOf() }.add(compound)
            setLangEntity(compound.id, compound)
        }

        for (corpusText in data.corpusTexts) {
            val addedCorpusText = CorpusText(
                corpusText.id,
                corpusText.text,
                corpusText.title,
                languageByShortName(corpusText.languageShortName)!!,
                corpusText.wordIds.map { id -> id?.let { allLangEntities[id] as? Word } }.toMutableList(),
                loadSource(corpusText.sourceRefs),
                corpusText.notes
            )
            corpus += addedCorpusText
            setLangEntity(corpusText.id, addedCorpusText)
        }
        for (translationData in data.translations) {
            val corpusText = allLangEntities[translationData.corpusTextId] as CorpusText
            val translation = Translation(
                translationData.id,
                corpusText,
                translationData.text,
                loadSource(translationData.sourceRefs),
                translationData.notes
            )
            setLangEntity(translation.id, translation)
            storeTranslation(corpusText, translation)
        }
        loadWordCategoryData(data.grammaticalCategories) { it.grammaticalCategories }
        loadWordCategoryData(data.wordClasses) { it.wordClasses }

        for (paradigm in data.paradigms) {
            while (paradigm.id > paradigms.size) {
                paradigms.add(null)
            }

            addParadigm(
                paradigm.name,
                languageByShortName(paradigm.languageShortName)!!,
                paradigm.posList
            ).apply {
                for (row in paradigm.rows) {
                    addRow(row)
                }
                for ((colIndex, column) in paradigm.columns.withIndex()) {
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

    private fun loadLanguageDetails(contentProviderCallback: (String) -> String) {
        for (language in languages.values) {
            val data = Json.decodeFromString<LanguageDetailsData>(contentProviderCallback(language.shortName + "/language.json"))
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
            language.stressRule = data.stressRuleId?.let { ruleRef(this, it) }
            language.phonotacticsRule = data.phonotacticsRuleId?.let { ruleRef(this, it) }
            language.orthographyRule = data.orthographyRuleId?.let { ruleRef(this, it) }
        }
    }

    private fun loadPublications(contentProviderCallback: (String) -> String) {
        val publicationsData = Json.decodeFromString<List<PublicationData>>(contentProviderCallback("publications.json"))
        for (pubData in publicationsData) {
            while (pubData.id > publications.size) {
                publications.add(null)
            }
            publications.add(Publication(pubData.id, pubData.name, pubData.refId))
        }
    }

    private fun loadWordCategoryData(wordCategoryData: List<WordCategoryData>, prop: (Language) -> MutableList<WordCategory>) {
        for (gc in wordCategoryData) {
            val language = languageByShortName(gc.languageShortName)
            prop(language!!).add(WordCategory(
                gc.name,
                gc.pos,
                gc.values.map { WordCategoryValue(it.name, it.abbreviation) }
            ))
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
                path.resolve(it).readText()
            }
            return result
        }

        fun fromJsonProvider(jsonProvider: (String) -> String): JsonGraphRepository {
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

        fun Rule.ruleToSerializedFormat() =
            RuleData(
                id,
                name,
                fromLanguage.shortName,
                toLanguage.shortName,
                logic.branches.map { branch ->
                    RuleBranchData(
                        branch.instructions.toSerializedFormat(),
                        branch.condition.toSerializedFormat()
                    )
                },
                addedCategories,
                replacedCategories,
                fromPOS,
                toPOS,
                source.sourceToSerializedFormat(),
                notes.takeIf { it != null },
                preInstructions = logic.preInstructions.toSerializedFormat().takeIf { it.isNotEmpty() }
            )

        private fun List<RuleInstruction>.toSerializedFormat(): List<RuleInstructionData> {
            return map { it.toSerializedFormat() }
        }

        fun RuleInstruction.toSerializedFormat() =
            RuleInstructionData(type, args = argsToSerializedFormat())

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
                is ChangePhonemeClassInstruction -> arrayOf(
                    oldClass,
                    newClass,
                    relativeIndex.toString()
                )
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
                target.relative
            )
            is LeafRuleCondition -> LeafRuleConditionData(
                type,
                phonemeClass?.name,
                parameter,
                negated,
                baseLanguageShortName
            )
            is OrRuleCondition -> OrRuleConditionData(members.map { it.toSerializedFormat()} )
            is AndRuleCondition -> AndRuleConditionData(members.map { it.toSerializedFormat()} )
            is OtherwiseCondition -> OtherwiseConditionData()
        }

        fun ruleBranchesFromSerializedFormat(
            result: InMemoryGraphRepository,
            fromLanguage: Language,
            branches: List<RuleBranchData>
        ): List<RuleBranch> {
            return branches.map { branchData ->
                RuleBranch(
                    branchData.condition!!.toRuntimeFormat(result, fromLanguage),
                    ruleInstructionsFromSerializedFormat(result, fromLanguage, branchData.instructions)
                )
            }
        }

        private fun ruleInstructionsFromSerializedFormat(
            result: InMemoryGraphRepository,
            fromLanguage: Language,
            ruleInstructionData: List<RuleInstructionData>
        ) = ruleInstructionData.map { insnData ->
            ruleInstructionFromSerializedFormat(result, fromLanguage, insnData)
        }

        fun ruleInstructionFromSerializedFormat(
            result: InMemoryGraphRepository,
            fromLanguage: Language,
            insnData: RuleInstructionData
        ): RuleInstruction =
            when (insnData.type) {
                InstructionType.ApplyRule ->
                    ApplyRuleInstruction(ruleRef(result, insnData.args[0].toInt()))
                InstructionType.ApplySoundRule ->
                    ApplySoundRuleInstruction(fromLanguage, ruleRef(result, insnData.args[0].toInt()), insnData.args.getOrNull(1))
                InstructionType.ApplyStress ->
                    ApplyStressInstruction(fromLanguage, insnData.args[0])
                InstructionType.Prepend, InstructionType.Append ->
                    PrependAppendInstruction(insnData.type, fromLanguage, insnData.args[0])
                InstructionType.Insert ->
                    InsertInstruction(insnData.args[0], insnData.args[1].toInt(), SeekTarget.parse(insnData.args[2], fromLanguage))
                InstructionType.ChangeSoundClass ->
                    ChangePhonemeClassInstruction(insnData.args.getOrNull(2)?.toInt() ?: 0,
                        insnData.args[0], insnData.args[1])
                else ->
                    RuleInstruction(insnData.type, insnData.args.firstOrNull() ?: "")
            }

        private fun ruleRef(repo: InMemoryGraphRepository, ruleId: Int) =
            RuleRef { repo.ruleById(ruleId) ?: throw IllegalStateException("Broken rule ID reference $ruleId") }
    }
}

fun main() {
    val ieRepo = JsonGraphRepository.fromJson(Path.of("data/ie"))
    ieRepo.save()

    val jrrtRepo = JsonGraphRepository.fromJson(Path.of("data/jrrt"))
    jrrtRepo.save()
}
