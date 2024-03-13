package ru.yole.etymograph

import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Serializable
data class LanguageData(val name: String, val shortName: String)

@Serializable
data class SourceRefData(val pubId: Int?, val refText: String)

@Serializable
data class WordData(
    val id: Int,
    val text: String,
    @SerialName("lang") val languageShortName: String,
    val gloss: String? = null,
    val fullGloss: String? = null,
    val pos: String? = null,
    val classes: List<String>? = null,
    val sourceRefs: List<SourceRefData>? = null,
    val notes: String? = null
)

@Serializable
data class PhonemeData(
    @SerialName("lang") val languageShortName: String,
    val graphemes: List<String>,
    val sound: String? = null,
    val classes: List<String> = emptyList()
)

@Serializable
data class DiphthongData(@SerialName("lang") val languageShortName: String, val diphthongs: List<String>)

@Serializable
data class LanguageRuleData(@SerialName("lang") val languageShortName: String, val ruleId: Int)

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

@Serializable
data class LeafRuleConditionData(
    @SerialName("cond") val type: ConditionType,
    @SerialName("cls") val phonemeClassName: String? = null,
    val characters: String? = null,
    val negated: Boolean = false
) : RuleConditionData() {
    override fun toRuntimeFormat(result: InMemoryGraphRepository, fromLanguage: Language): RuleCondition {
        val phonemeClass = requiredPhonemeClassByName(fromLanguage, phonemeClassName)
        return LeafRuleCondition(type, phonemeClass, characters, negated)
    }
}

@Serializable
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
            phonemeClassName?.let { fromLanguage.phonemeClassByName(it)!! },
            parameter
        )
    }
}

@Serializable
class RelativePhonemeRuleConditionData(
    val relativeIndex: Int,
    val negated: Boolean,
    @SerialName("tcls") val targetPhonemeClassName: String? = null,
    @SerialName("cls") val matchPhonemeClassName: String? = null,
    val parameter: String? = null
): RuleConditionData() {
    override fun toRuntimeFormat(result: InMemoryGraphRepository, fromLanguage: Language): RuleCondition {
        val targetPhonemeClass = requiredPhonemeClassByName(fromLanguage, targetPhonemeClassName)
        val matchPhonemeClass = requiredPhonemeClassByName(fromLanguage, matchPhonemeClassName)
        return RelativePhonemeRuleCondition(relativeIndex, negated, targetPhonemeClass, matchPhonemeClass, parameter)
    }
}

@Serializable
data class OrRuleConditionData(
    val members: List<RuleConditionData>
) : RuleConditionData() {
    override fun toRuntimeFormat(result: InMemoryGraphRepository, fromLanguage: Language): RuleCondition =
        OrRuleCondition(members.map { it.toRuntimeFormat(result, fromLanguage) })
}

@Serializable
data class AndRuleConditionData(
    val members: List<RuleConditionData>
) : RuleConditionData() {
    override fun toRuntimeFormat(result: InMemoryGraphRepository, fromLanguage: Language): RuleCondition =
        AndRuleCondition(members.map { it.toRuntimeFormat(result, fromLanguage) })
}

@Serializable
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
    val addedCategories: String?,
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
    val languages: List<LanguageData>,
    val phonemes: List<PhonemeData>,
    val diphthongs: List<DiphthongData>,
    val stressRules: List<LanguageRuleData>,
    val words: List<WordData>,
    val rules: List<RuleData>,
    val links: List<LinkData>,
    val corpusTexts: List<CorpusTextData>,
    val paradigms: List<ParadigmData>,
    val syllableStructures: List<SyllableStructureData>,
    val publications: List<PublicationData>,
    val compounds: List<CompoundData>,
    val translations: List<TranslationData>,
    val grammaticalCategories: List<WordCategoryData>,
    val wordClasses: List<WordCategoryData>,
    val phonotacticsRules: List<LanguageRuleData>,
    val ruleSequences: List<RuleSequenceData>? = null
)

class JsonGraphRepository(val path: Path?) : InMemoryGraphRepository() {
    private val allLinks = mutableListOf<Link>()

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
        path.writeText(toJson())
    }

    fun toJson(): String {
        val repoData = createGraphRepositoryData()
        return theJson.encodeToString(repoData)
    }

    private fun createGraphRepositoryData(): GraphRepositoryData {
        val phonemes = languages.values.flatMap { lang ->
            lang.phonemes.map { PhonemeData(lang.shortName, it.graphemes, it.sound, it.classes.toList()) }
        }
        val diphthongData = languages.values.map { DiphthongData(it.shortName, it.diphthongs) }
        val stressRuleData = mapLanguageRules { lang -> lang.stressRule }
        val phonotacticsRuleData = mapLanguageRules { lang -> lang.phonotacticsRule }
        val syllableStructureData = languages.values.mapNotNull { lang ->
            lang.syllableStructures.takeIf { it.isNotEmpty() }?.let { SyllableStructureData(lang.shortName, it) }
        }
        val grammarCategoryData = mapWordCategories { it.grammaticalCategories }
        val wordClassData = mapWordCategories { it.wordClasses }
        return GraphRepositoryData(
            languages.values.map { LanguageData(it.name, it.shortName) },
            phonemes,
            diphthongData,
            stressRuleData,
            allLangEntities.filterIsInstance<Word>().map {
                WordData(it.id, it.text, it.language.shortName, it.gloss, it.fullGloss, it.pos,
                    it.classes.takeIf { it.isNotEmpty() },
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
            publications.filterNotNull().map { PublicationData(it.id, it.name, it.refId) },
            allLangEntities.filterIsInstance<Compound>().map { c ->
                CompoundData(c.id, c.compoundWord.id, c.components.map { it.id }, c.source.sourceToSerializedFormat(), c.notes)
            },
            allLangEntities.filterIsInstance<Translation>().map { t ->
                TranslationData(t.id, t.corpusText.id, t.text, t.source.sourceToSerializedFormat(), t.notes)
            },
            grammarCategoryData,
            wordClassData,
            phonotacticsRuleData,
            allLangEntities.filterIsInstance<RuleSequence>().map { s ->
                RuleSequenceData(
                    s.id, s.name, s.fromLanguage.shortName, s.toLanguage.shortName,
                    s.rules.map { it.resolve().id },
                    s.source.sourceToSerializedFormat(), s.notes)
            }
        )
    }

    private fun mapLanguageRules(prop: (Language) -> RuleRef?) = languages.values.mapNotNull { lang ->
        prop(lang)?.let { LanguageRuleData(lang.shortName, it.resolve().id) }
    }

    private fun mapWordCategories(prop: (Language) -> List<WordCategory>) = languages.values.flatMap { lang ->
        prop(lang).map {
            WordCategoryData(lang.shortName, it.name, it.pos, it.values.map { v ->
                WordCategoryValueData(v.name, v.abbreviation)
            })
        }
    }

    private fun loadJson(string: String) {
        val data = Json.decodeFromString<GraphRepositoryData>(string)
        for (language in data.languages) {
            addLanguage(Language(language.name, language.shortName))
        }
        for (phoneme in data.phonemes) {
            languageByShortName(phoneme.languageShortName)!!.phonemes +=
                Phoneme(phoneme.graphemes, phoneme.sound, phoneme.classes.toSet())
        }
        for (diphthongData in data.diphthongs) {
            languageByShortName(diphthongData.languageShortName)!!.diphthongs = diphthongData.diphthongs
        }
        for (stressRuleData in data.stressRules) {
            languageByShortName(stressRuleData.languageShortName)!!.stressRule = ruleRef(this, stressRuleData.ruleId)
        }
        for (ruleData in data.phonotacticsRules) {
            languageByShortName(ruleData.languageShortName)!!.phonotacticsRule= ruleRef(this, ruleData.ruleId)
        }

        for (syllableStructureData in data.syllableStructures) {
            languageByShortName(syllableStructureData.languageShortName)!!.syllableStructures = syllableStructureData.syllableStructures
        }
        for (pubData in data.publications) {
            while (pubData.id > publications.size) {
                publications.add(null)
            }
            publications.add(Publication(pubData.id, pubData.name, pubData.refId))
        }
        for (word in data.words) {
            while (word.id > allLangEntities.size) {
                allLangEntities.add(null)
            }
            addWord(
                languageByShortName(word.languageShortName)!!,
                word.text,
                word.gloss,
                word.fullGloss,
                word.pos,
                word.classes ?: emptyList(),
                loadSource(word.sourceRefs),
                word.notes
            )
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
        for (sequence in data.ruleSequences ?: emptyList()) {
            val ruleSequence = RuleSequence(
                sequence.id,
                sequence.name,
                languageByShortName(sequence.fromLanguageShortName)
                    ?: throw IllegalStateException("Broken language ID reference ${sequence.fromLanguageShortName}"),
                languageByShortName(sequence.toLanguageShortName)!!,
                sequence.ruleIds.map { ruleRef(this, it) },
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
            result.loadJson(path.readText())
            return result
        }

        fun fromJsonString(string: String): JsonGraphRepository {
            val result = JsonGraphRepository(null)
            result.loadJson(string)
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
                notes,
                preInstructions = logic.preInstructions.toSerializedFormat()
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
                is ApplySoundRuleInstruction -> arrayOf(
                    ruleRef.resolve().id.toString(),
                    seekTarget.toEditableText()
                )
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
            is SyllableRuleCondition -> SyllableRuleConditionData(matchType, index, phonemeClass?.name, parameter)
            is RelativePhonemeRuleCondition -> RelativePhonemeRuleConditionData(
                relativeIndex,
                negated,
                targetPhonemeClass?.name,
                matchPhonemeClass?.name,
                parameter
            )
            is LeafRuleCondition -> LeafRuleConditionData(
                type,
                phonemeClass?.name,
                parameter,
                negated
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
                    ApplySoundRuleInstruction(fromLanguage, ruleRef(result, insnData.args[0].toInt()), insnData.args[1])
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
    val repo = JsonGraphRepository.fromJson(Path.of("ie.json"))
    repo.save()
}
