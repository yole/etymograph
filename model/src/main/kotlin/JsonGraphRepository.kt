package ru.yole.etymograph

import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Serializable
data class LanguageData(val name: String, val shortName: String)

@Serializable
data class WordData(
    val id: Int,
    val text: String,
    @SerialName("lang") val languageShortName: String,
    val gloss: String? = null,
    val fullGloss: String? = null,
    val pos: String? = null,
    val source: String? = null,
    val notes: String? = null
)

@Serializable
data class PhonemeClassData(@SerialName("lang") val languageShortName: String, val name: String, val phonemes: List<String>)

@Serializable
data class DigraphData(@SerialName("lang") val languageShortName: String, val digraphs: List<String>)

@Serializable
data class DiphthongData(@SerialName("lang") val languageShortName: String, val diphthongs: List<String>)

@Serializable
data class LetterNormalizationData(@SerialName("lang") val languageShortName: String, val rules: Map<String, String>)

@Serializable
data class StressRuleData(@SerialName("lang") val languageShortName: String, val ruleId: Int)

@Serializable
sealed class RuleConditionData {
    abstract fun toRuntimeFormat(result: InMemoryGraphRepository, fromLanguage: Language): RuleCondition
}

@Serializable
data class LeafRuleConditionData(
    @SerialName("cond") val type: ConditionType,
    @SerialName("cls") val phonemeClassName: String? = null,
    val characters: String? = null,
    val negated: Boolean = false
) : RuleConditionData() {
    override fun toRuntimeFormat(result: InMemoryGraphRepository, fromLanguage: Language): RuleCondition {
        return LeafRuleCondition(
            type,
            phonemeClassName?.let { className -> fromLanguage.phonemeClassByName(className) },
            characters,
            negated
        )
    }
}

@Serializable
class SyllableRuleConditionData(
    val matchType: SyllableMatchType,
    val index: Int,
    @SerialName("cls") val phonemeClassName: String,
) : RuleConditionData() {
    override fun toRuntimeFormat(result: InMemoryGraphRepository, fromLanguage: Language): RuleCondition {
        return SyllableRuleCondition(
            matchType,
            index,
            fromLanguage.phonemeClassByName(phonemeClassName)!!
        )
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
    val source: String? = null,
    val notes: String? = null,
    val preInstructions: List<RuleInstructionData>? = null
)

@Serializable
data class LinkData(
    @SerialName("from") val fromWordId: Int,
    @SerialName("to") val toWordId: Int,
    val type: String,
    val ruleIds: List<Int>? = null,
    val source: String? = null,
    val notes: String? = null
)

@Serializable
data class CorpusTextData(
    val id: Int, val text: String, val title: String?,
    @SerialName("lang") val languageShortName: String,
    val wordIds: List<Int?>,
    val source: String? = null,
    val notes: String? = null
)

@Serializable
data class ParadigmRuleSeqData(
    val ruleIds: List<Int>?
)

@Serializable
data class ParadigmCellData(
    val alternatives: List<ParadigmRuleSeqData>? = null
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
    val pos: String,
    val rows: List<String>,
    val columns: List<ParadigmColumnData>
)

@Serializable
data class GraphRepositoryData(
    val languages: List<LanguageData>,
    val phonemeClasses: List<PhonemeClassData>,
    val digraphs: List<DigraphData>,
    val diphthongs: List<DiphthongData>,
    val letterNormalization: List<LetterNormalizationData>,
    val stressRules: List<StressRuleData>,
    val words: List<WordData>,
    val rules: List<RuleData>,
    val links: List<LinkData>,
    val corpusTexts: List<CorpusTextData>,
    val paradigms: List<ParadigmData>,
)

class JsonGraphRepository(val path: Path?) : InMemoryGraphRepository() {
    private val allLinks = mutableListOf<Link>()

    override fun createLink(
        fromEntity: LangEntity,
        toEntity: LangEntity,
        type: LinkType,
        rules: List<Rule>,
        source: String?,
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
        val phonemeClassData = languages.values.flatMap { lang ->
            lang.phonemeClasses.map { PhonemeClassData(lang.shortName, it.name, it.matchingPhonemes) }
        }
        val digraphData = languages.values.map { DigraphData(it.shortName, it.digraphs) }
        val diphthongData = languages.values.map { DiphthongData(it.shortName, it.diphthongs) }
        val letterNormalizationData = languages.values.map { LetterNormalizationData(it.shortName, it.letterNormalization) }
        val stressRuleData = languages.values.mapNotNull { lang ->
            lang.stressRule?.let { StressRuleData(lang.shortName, it.resolve().id) }
        }
        return GraphRepositoryData(
            languages.values.map { LanguageData(it.name, it.shortName) },
            phonemeClassData,
            digraphData,
            diphthongData,
            letterNormalizationData,
            stressRuleData,
            allLangEntities.filterIsInstance<Word>().map {
                WordData(it.id, it.text, it.language.shortName, it.gloss, it.fullGloss, it.pos, it.source, it.notes)
            },
            rules.map { it.ruleToSerializedFormat() },
            allLinks.map { link ->
                LinkData(
                    link.fromEntity.id, link.toEntity.id,
                    link.type.id, link.rules.takeIf { it.isNotEmpty() }?.map { it.id }, link.source, link.notes
                )
            },
            corpus.map {
                CorpusTextData(
                    it.id, it.text, it.title, it.language.shortName,
                    it.words.map { it?.id }, it.source, it.notes
                )
            },
            paradigms.map {
                ParadigmData(it.id, it.name, it.language.shortName, it.pos, it.rowTitles, it.columns.map { col ->
                    ParadigmColumnData(col.title, col.cells.map { cell ->
                        ParadigmCellData(alternatives = cell?.alternatives?.map { alt ->
                            ParadigmRuleSeqData(alt.rules.map { it.id })
                        })
                    })
                })
            },
        )
    }

    private fun loadJson(string: String) {
        val data = Json.decodeFromString<GraphRepositoryData>(string)
        for (language in data.languages) {
            addLanguage(Language(language.name, language.shortName))
        }
        for (phonemeClass in data.phonemeClasses) {
            languageByShortName(phonemeClass.languageShortName)!!.phonemeClasses.add(
                PhonemeClass(phonemeClass.name, phonemeClass.phonemes))
        }
        for (digraphData in data.digraphs) {
            languageByShortName(digraphData.languageShortName)!!.digraphs = digraphData.digraphs
        }
        for (diphthongData in data.diphthongs) {
            languageByShortName(diphthongData.languageShortName)!!.diphthongs = diphthongData.diphthongs
        }
        for (letterNormalizationData in data.letterNormalization) {
            languageByShortName(letterNormalizationData.languageShortName)!!.letterNormalization = letterNormalizationData.rules
        }
        for (stressRuleData in data.stressRules) {
            languageByShortName(stressRuleData.languageShortName)!!.stressRule = ruleRef(this, stressRuleData.ruleId)
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
                word.source,
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
                rule.source,
                rule.notes
            )
            rules.add(addedRule)
            setLangEntity(rule.id, addedRule)
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
                    link.source,
                    link.notes
                )
            }
        }
        for (corpusText in data.corpusTexts) {
            val addedCorpusText = CorpusText(
                corpusText.id,
                corpusText.text,
                corpusText.title,
                languageByShortName(corpusText.languageShortName)!!,
                corpusText.wordIds.map { id -> id?.let { allLangEntities[id] as? Word } }.toMutableList(),
                corpusText.source,
                corpusText.notes
            )
            corpus += addedCorpusText
            setLangEntity(corpusText.id, addedCorpusText)
        }
        for (paradigm in data.paradigms) {
            addParadigm(
                paradigm.name,
                languageByShortName(paradigm.languageShortName)!!,
                paradigm.pos
            ).apply {
                for (row in paradigm.rows) {
                    addRow(row)
                }
                for ((colIndex, column) in paradigm.columns.withIndex()) {
                    addColumn(column.title)
                    for ((rowIndex, cell) in column.cells.withIndex()) {
                        val alternatives = cell.alternatives
                        if (alternatives != null) {
                            val rules = alternatives.map { alt -> alt.ruleIds?.map { ruleById(it)!! } ?: emptyList() }
                            setRule(rowIndex, colIndex, rules)
                        }
                    }
                }
            }
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
                source,
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
                else -> if (type.takesArgument) arrayOf(arg) else emptyArray()
            }

        private fun RuleCondition.toSerializedFormat(): RuleConditionData = when(this) {
            is SyllableRuleCondition -> SyllableRuleConditionData(matchType, index, phonemeClass.name)
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
                InstructionType.Prepend ->
                    PrependInstruction(fromLanguage, insnData.args[0])
                else ->
                    RuleInstruction(insnData.type, insnData.args.firstOrNull() ?: "")
            }

        private fun ruleRef(repo: InMemoryGraphRepository, ruleId: Int) =
            RuleRef { repo.ruleById(ruleId)!! }
    }
}

fun main() {
    val repo = JsonGraphRepository.fromJson(Path.of("jrrt.json"))
    repo.save()
}
