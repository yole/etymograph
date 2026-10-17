package page.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.io.path.exists
import page.yole.etymograph.JsonGraph.Companion.ruleBranchesFromSerializedFormat
import page.yole.etymograph.JsonGraph.Companion.ruleToSerializedFormat

class JsonGraphTest {
    @get:org.junit.Rule
    val temporaryFolder = TemporaryFolder()

    lateinit var graph: JsonGraph
    lateinit var q: Language
    lateinit var ce: Language

    @Before
    fun setup() {
        graph = JsonGraph(null)
        q = quenya(graph)
        ce = graph.addLanguage("Common Eldarin", "CE")
    }

    @Test
    fun deletedWords() {
        val abc = graph.addWord("abc", q, null)
        val def = graph.addWord("def", q, null)
        graph.deleteWord(abc)

        val repo2 = graph.roundtrip()
        assertEquals(null, repo2.wordById(abc.id))
        assertEquals("def", repo2.wordById(def.id)!!.text)
    }

    @Test
    fun deleteCorpusText() {
        val text1 = graph.addCorpusText("abc", "Text 1", q)
        val text2 = graph.addCorpusText("def", "Text 2", q)
        graph.addTranslation(text1, "ghi", emptyList())
        graph.deleteCorpusText(text1)

        assertEquals(1, graph.allCorpusTexts().count())
        val repo2 = graph.roundtrip()
        assertEquals(1, repo2.allCorpusTexts().count())
        assertEquals(null, repo2.corpusTextById(text1.id))
        assertEquals("Text 2", repo2.corpusTextById(text2.id)!!.title)
    }

    @Test
    fun serializeApplySoundRule() {
        val soundRule = graph.addRule(
            "q-lengthen", q, q,
            Rule.parseLogic("* a > á", q.parseContext()))

        val parseContext = RuleParseContext(q, q) {
            if (it == "q-lengthen") RuleRef.to(soundRule) else throw RuleParseException("no such rule")
        }
        val applySoundRule = Rule(-1, "lengthen-first-vowel", q, q, Rule.parseLogic("""
            - apply sound rule 'q-lengthen' to first vowel
        """.trimIndent(), parseContext))

        val serializedData = applySoundRule.ruleToSerializedFormat()
        assertEquals(2, serializedData.branches[0].instructions[0].args.size)

        val branches = ruleBranchesFromSerializedFormat(graph, q, q, serializedData.branches)
        val insn = branches[0].instructions[0] as ApplySoundRuleInstruction
        assertEquals("q-lengthen", insn.ruleRef.resolve().name)
        assertEquals("vowel", insn.seekTarget!!.phonemeClass!!.name)
    }

    @Test
    fun serializeRelativePhonemeRule() {
        val text = "i > 0 if previous sound is not vowel"
        val rule = parseRule(q, q, "* $text")

        val serializedData = rule.ruleToSerializedFormat()
        val branches = ruleBranchesFromSerializedFormat(graph, q, q, serializedData.branches)
        assertEquals(text, branches[0].instructions[0].toEditableText(graph))
    }

    @Test
    fun serializePhonemeEqualsCondition() {
        val text = "C > 0 if sound is same as previous sound"
        val rule = parseRule(q, q, "* $text")

        val serializedData = rule.ruleToSerializedFormat()
        val branches = ruleBranchesFromSerializedFormat(graph, q, q, serializedData.branches)
        assertEquals(text, branches[0].instructions[0].toEditableText(graph))
    }

    @Test
    fun serializeSpeRule() {
        val rule = parseRule(q, q, "* d > l / #_ if number of syllables is 1")

        val serializedData = rule.ruleToSerializedFormat()
        val branches = ruleBranchesFromSerializedFormat(graph, q, q, serializedData.branches)
        val insn = branches[0].instructions[0]
        assertTrue(insn is SpeInstruction)
        assertEquals("d > l / #_ if number of syllables is 1", insn.toEditableText(graph))
    }

    @Test
    fun serializeInsertInstruction() {
        val rule = parseRule(q, q, """
            - insert 'i' before last consonant
        """.trimIndent())

        val serializedData = rule.ruleToSerializedFormat()
        val branches = ruleBranchesFromSerializedFormat(graph, q, q, serializedData.branches)
        assertEquals("insert 'i' before last consonant", branches[0].instructions[0].toEditableText(graph))
    }

    @Test
    fun serializeBranchComment() {
        val rule = parseRule(q, q, """
            $ This is a comment
            - insert 'i' before last consonant
        """.trimIndent())

        val serializedData = rule.ruleToSerializedFormat()
        val branches = ruleBranchesFromSerializedFormat(graph, q, q, serializedData.branches)
        assertEquals("This is a comment", branches[0].comment)
    }

    @Test
    fun serializeInstructionComment() {
        val rule = parseRule(q, q, """
            $ This is a comment
            - insert 'i' before last consonant
            $ This is an instruction comment
            - insert 'i' before last consonant
        """.trimIndent())

        val serializedData = rule.ruleToSerializedFormat()
        val branches = ruleBranchesFromSerializedFormat(graph, q, q, serializedData.branches)
        assertEquals("This is an instruction comment", branches[0].instructions[1].comment)
    }

    @Test
    fun serializePostInstructions() {
        val rule = parseRule(q, q, """
            word ends with 'i':
            - append 'r'
            = append 'e'
        """.trimIndent())
        val serializedData = rule.ruleToSerializedFormat()
        val rule2 = graph.ruleFromSerializedFormat(serializedData, q, q)
        assertEquals(1, (rule2.logic as MorphoRuleLogic).postInstructions.size)
    }

    @Test
    fun serializeNot() {
        val text = "* a > i if not (previous sound is 'c')"
        val rule = parseRule(q, q, text)
        val serializedData = rule.ruleToSerializedFormat()
        val rule2 = graph.ruleFromSerializedFormat(serializedData, q, q)
        assertEquals(text, rule2.toEditableText())
    }

    @Test
    fun serializeTranslation() {
        val corpusText = graph.addCorpusText("abc", null, q)
        val translation = graph.addTranslation(corpusText, "def", emptyList())
        translation.anchorStartIndex = 0
        translation.anchorEndIndex = 1
        val repo2 = graph.roundtrip()
        val corpusText2 = repo2.corpusTextById(corpusText.id)!!
        val translation2 = repo2.translationsForText(corpusText2).single()
        assertEquals(0, translation2.anchorStartIndex)
        assertEquals(1, translation2.anchorEndIndex)
    }

    private fun JsonGraph.roundtrip(): JsonGraph {
        val jsonFiles = mutableMapOf<String, String>()
        saveToJson { path, content -> jsonFiles[path] = content }
        return JsonGraph.fromJsonProvider { jsonFiles[it] }
    }

    @Test
    fun serializeDerivedCorpusOpenCompoundAssociation() {
        val head = graph.addWord("lassi", q, "leaves")
        val form = graph.addWord("lassi", q, "leaves.ACC")
        val transcription = graph.addWord("lassie", q, "transcription")
        val rule = q.rule("- no change", name = "accusative", addedCategories = ".ACC")
        graph.addLink(form, head, Link.Derived, listOf(rule))
        graph.addLink(transcription, form, Link.Transcription)
        val compound = graph.createOpenCompound(listOf(head), 0, "foliage")
        val text = graph.addCorpusText("lassie", "Test", q)
        text.associateWord(0, transcription, openCompound = compound)

        val reloaded = graph.roundtrip()
        val loadedText = reloaded.corpusTextById(text.id)!!
        assertEquals(transcription.id, loadedText.words.single().word.id)
        assertEquals(compound.id, loadedText.words.single().openCompound?.id)
        assertEquals("foliage.ACC", loadedText.mapToLines().single().corpusWords.single().gloss)
    }

    @Test
    fun serializeCorpusOpenCompoundAssociations() {
        val head = graph.addWord("lassi", q, "leaves")
        val modifier = graph.addWord("laurie", q, "golden")
        val text = graph.addCorpusText("laurie lassi", "Test", q)
        val compound = graph.createOpenCompound(listOf(modifier, head), 1, "foliage")
        text.associateWord(0, modifier, openCompound = compound)
        text.associateWord(1, head, "context gloss", compound)

        val reloaded = graph.roundtrip()
        val loadedText = reloaded.corpusTextById(text.id)!!
        val loadedCompound = reloaded.langEntityById(compound.id) as OpenCompound
        assertTrue(loadedText.words.all { it.openCompound === loadedCompound })
        assertEquals("context gloss", loadedText.words[1].contextGloss)
        assertEquals(listOf("...", "foliage"), loadedText.mapToLines().single().corpusWords.map { it.gloss })

        reloaded.deleteOpenCompound(loadedCompound)
        val afterDeletion = reloaded.roundtrip().corpusTextById(text.id)!!
        assertTrue(afterDeletion.words.all { it.openCompound == null })
        assertEquals(listOf("golden", "leaves"), afterDeletion.mapToLines().single().corpusWords.map { it.gloss })
    }

    @Test
    fun serializeCorpusAssociationWithoutOpenCompound() {
        val word = graph.addWord("lassi", q, "leaves")
        val text = graph.addCorpusText("lassi", "Test", q)
        text.associateWord(0, word, "context gloss")

        val files = mutableMapOf<String, String>()
        graph.saveToJson { path, content -> files[path] = content }
        assertTrue("openCompoundId" !in files.getValue("Q/corpus.json"))
        val loaded = JsonGraph.fromJsonProvider { files[it] }.corpusTextById(text.id)!!
        assertEquals(null, loaded.words.single().openCompound)
        assertEquals("context gloss", loaded.words.single().contextGloss)
        assertEquals("leaves", loaded.mapToLines().single().corpusWords.single().gloss)
    }

    @Test
    fun serializeOpenCompound() {
        val fara = graph.addWord("fara", q, "hunt")
        val mir = graph.addWord("mir", q, "jewel")
        val publication = graph.addPublication("Test publication", "TEST")
        val source = listOf(SourceRef(publication.id, "42"))
        val compound = graph.createOpenCompound(listOf(fara, mir), 1, "hunting jewel",
            "A jewel used for hunting", source, "Notes")
        graph.addLink(compound, mir, Link.Related)

        val reloaded = graph.roundtrip()
        val loaded = reloaded.langEntityById(compound.id) as OpenCompound
        assertEquals(listOf(fara.id, mir.id), loaded.components.map { it.id })
        assertEquals(1, loaded.headIndex)
        assertEquals(mir.id, loaded.headComponent().id)
        assertEquals(compound.gloss, loaded.gloss)
        assertEquals(compound.fullGloss, loaded.fullGloss)
        assertEquals(source, loaded.source)
        assertEquals("Notes", loaded.notes)
        assertEquals(mir.id, reloaded.getLinksFrom(loaded).single().toEntity.id)
    }

    @Test
    fun openCompoundFilesExistOnlyForLanguagesWithOpenCompounds() {
        val path = temporaryFolder.newFolder().toPath()
        val graph = JsonGraph(path)
        val q = graph.addLanguage("Quenya", "Q")
        graph.addLanguage("Common Eldarin", "CE")
        val compoundFile = path.resolve("Q/open-compounds.json")
        val emptyLanguageFile = path.resolve("CE/open-compounds.json")
        graph.save()
        assertEquals(false, compoundFile.exists())
        assertEquals(false, emptyLanguageFile.exists())

        val word = graph.addWord("mir", q, "jewel")
        val compound = graph.createOpenCompound(listOf(word), 0)
        graph.save()
        assertTrue(compoundFile.exists())
        assertEquals(false, emptyLanguageFile.exists())
        assertEquals(1, JsonGraph.fromJson(path).allOpenCompounds().size)

        graph.deleteOpenCompound(compound)
        graph.save()
        assertEquals(false, compoundFile.exists())

        val reloaded = JsonGraph.fromJson(path)
        assertTrue(reloaded.allOpenCompounds().isEmpty())
        assertEquals(null, reloaded.langEntityById(compound.id))
    }

    @Test
    fun serializeDeleteParadigm() {
        val np = graph.addParadigm("Noun", q, listOf("N"))
        val vp = graph.addParadigm("Verb", q, listOf("V"))
        graph.deleteParadigm(np)
        assertEquals(1, graph.allParadigms().size)
        val repo2 = graph.roundtrip()
        assertEquals(1, repo2.allParadigms().size)
        assertEquals("Verb", repo2.paradigmById(vp.id)!!.name)
    }

    @Test
    fun serializePhonemes() {
        q.phonemes = mutableListOf(phoneme("ā", "long front open vowel").apply {
            simplifiedGrapheme = "a"
        })
        val repo2 = graph.roundtrip()
        val phoneme = repo2.languageByShortName("Q")!!.phonemes.single()
        assertEquals("a", phoneme.simplifiedGrapheme)
    }

    @Test
    fun serializeRuleSequence() {
        setupRuleSequence()
        val repo2 = graph.roundtrip()
        val sequences = repo2.ruleSequencesForLanguage(repo2.languageByShortName("Q")!!)
        assertEquals(1, sequences.size)
        assertEquals("i-disappears", sequences[0].resolveRules().single().name)
    }

    private fun setupRuleSequence(): RuleSequence {
        val rule = graph.addRule(
            "i-disappears", ce, q,
            Rule.parseLogic("* i > 0 / a_ ", q.parseContext())
        )
        return graph.addRuleSequence("ce-to-q", ce, q, listOf(RuleSequenceStep(rule, null,false, false)))
    }

    @Test
    fun serializeOrthographyRule() {
        val rule = graph.addRule("q-ortho", q, q,
            Rule.parseLogic("* u > j / #_", q.parseContext()))
        q.orthographyRule = RuleRef.to(rule)

        val repo2 = graph.roundtrip()
        assertEquals("q-ortho", repo2.languageByShortName("Q")!!.orthographyRule!!.resolve().name)
    }

    @Test
    fun serializeExplicitStress() {
        val word = graph.findOrAddWord("ea", q, null)
        word.setExplicitStress(1)
        val repo2 = graph.roundtrip()
        val word2 = repo2.wordsByText(repo2.languageByShortName("Q")!!, "ea").single()
        assertEquals(1, word2.stressedPhonemeIndex)
        assertEquals(true, word2.explicitStress)
    }

    @Test
    fun serializeCompound() {
        val baseWord = graph.addWord("mann", q, null)
        val prefix = graph.addWord("sæ", q, null)
        val compoundWord = graph.addWord("sæmann", q, null)
        graph.createCompound(compoundWord, listOf(prefix, baseWord), headIndex = 1)
        val repo2 = graph.roundtrip()
        val compoundWord2 = repo2.wordsByText(repo2.languageByShortName("Q")!!, "sæmann").single()
        val compound2 = repo2.findCompoundsByCompoundWord(compoundWord2).single()
        assertEquals(1, compound2.headIndex)
    }

    @Test
    fun serializeParadigmRules() {
        val paradigm = graph.addParadigm("Noun", q, listOf("N"))
        val preRule = q.rule("word ends with 'a':\n- change ending to ''", name = "q-pre")
        val postRule = q.rule("word ends with 'oo':\n - change ending to 'o'", name = "q-post")
        paradigm.preRule = preRule
        paradigm.postRule = postRule
        val repo2 = graph.roundtrip()
        assertEquals(1, repo2.paradigmsForLanguage(repo2.languageByShortName("Q")!!).size)
        assertEquals("q-pre", repo2.paradigmById(paradigm.id)!!.preRule!!.name)
        assertEquals("q-post", repo2.paradigmById(paradigm.id)!!.postRule!!.name)
    }

    @Test
    fun serializeLinkRuleSequence() {
        val seq = setupRuleSequence()
        val lai = graph.addWord("lai", language = ce, gloss = null)
        val la = graph.addWord("la", language = q, gloss = null)
        val link = graph.addLink(la, lai, Link.Origin)
        graph.applyRuleSequence(link, seq)
        val repo2 = graph.roundtrip()
        val la2 = repo2.wordById(la.id)!!
        val link2 = repo2.getLinksFrom(la2).single()
        assertEquals(seq.name, link2.sequence!!.name)
    }

    @Test
    fun serializeProtoLanguage() {
        val qLang = graph.languageByShortName("Q")!!
        qLang.protoLanguage = ce

        val repo2 = graph.roundtrip()
        val q2 = repo2.languageByShortName("Q")!!
        assertEquals("CE", q2.protoLanguage?.shortName)
    }

    @Test
    fun serializeAccentTypes() {
        val accentTypes = mutableSetOf(AccentType.Acute, AccentType.Circumflex)
        q.accentTypes = accentTypes
        val repo2 = graph.roundtrip()
        val q2 = repo2.languageByShortName("Q")!!
        assertEquals(accentTypes, q2.accentTypes)
    }

    @Test
    fun serializeSyllabographic() {
        val ht = graph.addLanguage("Hittite", "Ht")
        val word = graph.addWord("pé-ra-an", ht, gloss = null, syllabographic = true)
        val graph2 = graph.roundtrip()
        val word2 = graph2.wordById(word.id)!!
        assertEquals(true, word2.syllabographic)
    }

    @Test
    fun serializeLanguageSyllabographic() {
        val ht = graph.addLanguage("Hittite", "Ht")
        ht.syllabographic = true
        val graph2 = graph.roundtrip()
        val ht2 = graph2.languageByShortName("Ht")!!
        assertEquals(true, ht2.syllabographic)
    }
}
