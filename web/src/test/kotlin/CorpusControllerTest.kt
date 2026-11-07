package page.yole.etymograph.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.BDDMockito.given
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import page.yole.etymograph.*
import page.yole.etymograph.web.controllers.CorpusController
import page.yole.etymograph.web.controllers.RuleController
import page.yole.etymograph.web.controllers.TranslationController

class CorpusControllerTest {
    private lateinit var fixture: QTestFixture
    private lateinit var corpusController: CorpusController
    private lateinit var graph: InMemoryGraph

    @Before
    fun setup() {
        fixture = QTestFixture()
        graph = fixture.graph
        corpusController = CorpusController()
    }

    @Test
    fun removeOpenCompoundApiPreservesOtherOccurrencesAndWordAssociations() {
        val modifier = graph.addWord("laurie", fixture.q, "golden")
        val head = graph.addWord("lasse", fixture.q, "leaf")
        val compound = graph.createOpenCompound(listOf(modifier, head), 1, "foliage")
        val text = graph.addCorpusText("laurie lasse laurie lasse", "Test", fixture.q)
        text.associateOpenCompound(1, compound)
        text.associateOpenCompound(3, compound)
        val service = mock(GraphService::class.java)
        given(service.resolveGraph("test")).willReturn(graph)
        val mvc = MockMvcBuilders.standaloneSetup(corpusController)
            .setCustomArgumentResolvers(GraphArgumentResolver(service)).build()
        mvc.perform(get("/test/corpus/text/${text.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.lines[0].words[1].openCompoundId").value(compound.id))

        mvc.perform(post("/test/corpus/text/${text.id}/openCompound/remove").contentType(MediaType.APPLICATION_JSON)
            .content("""{"index":3,"compoundId":${compound.id}}"""))
            .andExpect(status().isOk)

        val corpusWords = corpusController.textJson(graph, text.id).lines.single().words
        assertEquals(listOf(compound.id, compound.id, null, null), corpusWords.map { it.openCompoundId })
        assertEquals(listOf("...", "foliage", "golden", "leaf"), corpusWords.map { it.gloss })
        assertEquals(listOf(modifier.id, head.id, modifier.id, head.id), text.words.map { it.word.id })
        mvc.perform(post("/test/corpus/text/${text.id}/openCompound/remove").contentType(MediaType.APPLICATION_JSON)
            .content("""{"index":3,"compoundId":${compound.id}}"""))
            .andExpect(status().isBadRequest)
        assertEquals(listOf(compound.id, compound.id, null, null),
            text.words.map { it.openCompound?.id })
    }

    @Test
    fun openCompoundCandidateApiAssociatesAllMatchedOccurrences() {
        val modifier = graph.addWord("laurie", fixture.q, "golden")
        val head = graph.addWord("lasse", fixture.q, "leaf")
        val compound = graph.createOpenCompound(listOf(modifier, head), 1, "foliage")
        val text = graph.addCorpusText("laurie laurie lasse", "Test", fixture.q)
        val service = mock(GraphService::class.java)
        given(service.resolveGraph("test")).willReturn(graph)
        val mvc = MockMvcBuilders.standaloneSetup(corpusController)
            .setCustomArgumentResolvers(GraphArgumentResolver(service)).build()

        mvc.perform(get("/test/corpus/text/${text.id}/openCompounds/2"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(compound.id))
            .andExpect(jsonPath("$[0].components[0].id").value(modifier.id))
            .andExpect(jsonPath("$[0].components[1].id").value(head.id))
            .andExpect(jsonPath("$[0].gloss").value("foliage"))
        assertTrue(text.words.isEmpty())

        mvc.perform(post("/test/corpus/text/${text.id}/openCompound").contentType(MediaType.APPLICATION_JSON)
            .content("""{"index":2,"compoundId":${compound.id}}"""))
            .andExpect(status().isOk)
        assertEquals(listOf(1, 2), text.words.map { it.index })
        assertTrue(text.words.all { it.openCompound === compound })
        assertEquals(listOf("golden", "...", "foliage"),
            corpusController.textJson(graph, text.id).lines.single().words.map { it.gloss })
        mvc.perform(get("/test/corpus/text/${text.id}/openCompounds/99"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun associateOpenCompoundApiRechecksAvailabilityWithoutPartialChanges() {
        val modifier = graph.addWord("laurie", fixture.q, "golden")
        val head = graph.addWord("lasse", fixture.q, "leaf")
        val compound = graph.createOpenCompound(listOf(modifier, head), 1)
        val other = graph.createOpenCompound(listOf(modifier), 0)
        val text = graph.addCorpusText("laurie lasse", "Test", fixture.q)
        val service = mock(GraphService::class.java)
        given(service.resolveGraph("test")).willReturn(graph)
        val mvc = MockMvcBuilders.standaloneSetup(corpusController)
            .setCustomArgumentResolvers(GraphArgumentResolver(service)).build()
        assertEquals(compound.id, corpusController.openCompoundCandidates(graph, text.id, 1).single().id)

        text.associateWord(0, modifier, openCompound = other)
        mvc.perform(post("/test/corpus/text/${text.id}/openCompound").contentType(MediaType.APPLICATION_JSON)
            .content("""{"index":1,"compoundId":${compound.id}}"""))
            .andExpect(status().isBadRequest)
        assertEquals(1, text.words.size)
        assertEquals(other.id, text.words.single().openCompound?.id)
        mvc.perform(post("/test/corpus/text/${text.id}/openCompound").contentType(MediaType.APPLICATION_JSON)
            .content("""{"index":1,"compoundId":9999}"""))
            .andExpect(status().isNotFound)
    }

    @Test
    fun alternatives() {
        val corpusParams = CorpusController.CorpusTextParams(text = "Elen sila...")
        val corpusTextViewModel = corpusController.newText(graph, "q", corpusParams)

        graph.findOrAddWord("elen", fixture.q, "star", pos = "N")
        val (_, accRule) = fixture.setupParadigm()

        val alternatives = corpusController.requestAlternatives(graph, corpusTextViewModel.id, 0)
        assertEquals(1, alternatives.size)
        assertEquals("star.ACC", alternatives[0].gloss)
        assertEquals(accRule.id, alternatives[0].ruleId)

        corpusController.acceptAlternative(graph, corpusTextViewModel.id,
            CorpusController.AcceptAlternativeParameters(0, alternatives[0].wordId, alternatives[0].ruleId))
        val word = graph.corpusTextById(corpusTextViewModel.id)!!.wordByIndex(0)!!
        assertEquals("star.ACC", word.getOrComputeGloss())
    }

    @Test
    fun stress() {
        fixture.q.phonemes = listOf(
            Phoneme(-1, listOf("a"), sound = null, classes = setOf("vowel")),
            Phoneme(-1, listOf("i"), sound = null, classes = setOf("vowel")),
            Phoneme(-1, listOf("e"), sound = null, classes = setOf("vowel"))
        )

        val ruleController = RuleController()
        ruleController.newRule(
            graph,
            RuleController.UpdateRuleParameters(
                "q-stress",
                "q", "q",
                "- stress is on first syllable",
            ))

        fixture.q.stressRule = RuleRef.to(graph.ruleByName("q-stress")!!)

        val elen = graph.findOrAddWord("elen", fixture.q, "star")
        val sila = graph.findOrAddWord("sila", fixture.q, "shines")

        val corpusParams = CorpusController.CorpusTextParams(text = "Elen sila...")
        val corpusTextViewModel = corpusController.newText(graph, "q", corpusParams)
        val id = corpusTextViewModel.id

        corpusController.associateWord(graph, id, CorpusController.AssociateWordParameters(0, elen.id))
        corpusController.associateWord(graph, id, CorpusController.AssociateWordParameters(1, sila.id))

        val corpusTextViewModel2 = corpusController.textJson(graph, id)
        val line = corpusTextViewModel2.lines.single()
        assertEquals(0, line.words[0].stressIndex)
        assertEquals(1, line.words[0].stressLength)
    }

    @Test
    fun derivedOpenCompoundGloss() {
        val head = graph.addWord("lassi", fixture.q, "leaves")
        val form = graph.addWord("lassi", fixture.q, "leaves.ACC")
        val (_, rule) = fixture.setupParadigm()
        graph.addLink(form, head, Link.Derived, listOf(rule))
        val compound = graph.createOpenCompound(listOf(head), 0, "foliage")
        val text = graph.addCorpusText("lassi", "Test", fixture.q)
        text.associateWord(0, form, openCompound = compound)

        val response = corpusController.textJson(graph, text.id)
        assertEquals("foliage.ACC", response.lines.single().words.single().gloss)
    }

    @Test
    fun openCompoundGloss() {
        val modifier = graph.addWord("laurie", fixture.q, "golden")
        val head = graph.addWord("lassi", fixture.q, "leaves")
        val compound = graph.createOpenCompound(listOf(modifier, head), 1, "foliage")
        val text = graph.addCorpusText("laurie lassi", "Test", fixture.q)
        text.associateWord(0, modifier, openCompound = compound)
        text.associateWord(1, head, openCompound = compound)

        val response = corpusController.textJson(graph, text.id)
        assertEquals(listOf("...", "foliage"), response.lines.single().words.map { it.gloss })
    }

    @Test
    fun contextGloss() {
        val corpusParams = CorpusController.CorpusTextParams(text = "Ai laurie lantar")
        val corpusTextViewModel = corpusController.newText(graph, "q", corpusParams)

        val word = graph.findOrAddWord("laurie", fixture.q, "golden")
        corpusController.associateWord(graph, corpusTextViewModel.id, CorpusController.AssociateWordParameters(
            1, word.id, "goldenly"
        ))

        val textJson = corpusController.textJson(graph, corpusTextViewModel.id)
        assertEquals("goldenly", textJson.lines.single().words[1].contextGloss)
    }

    @Test
    fun updateContextGlossPreservesAssociations() {
        val modifier = graph.addWord("laurie", fixture.q, "golden")
        val head = graph.addWord("lasse", fixture.q, "leaf")
        val compound = graph.createOpenCompound(listOf(modifier, head), 1, "foliage")
        val text = graph.addCorpusText("laurie lasse laurie lasse", "Test", fixture.q)
        text.associateWord(0, modifier, "original", compound)
        text.associateWord(2, modifier, "other occurrence", compound)
        val service = mock(GraphService::class.java)
        given(service.resolveGraph("test")).willReturn(graph)
        val mvc = MockMvcBuilders.standaloneSetup(corpusController)
            .setCustomArgumentResolvers(GraphArgumentResolver(service)).build()

        mvc.perform(post("/test/corpus/text/${text.id}/contextGloss")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"index":0,"contextGloss":"goldenly"}"""))
            .andExpect(status().isOk)

        val words = corpusController.textJson(graph, text.id).lines.single().words
        assertEquals("goldenly", words[0].contextGloss)
        assertEquals(modifier.id, words[0].wordId)
        assertEquals(compound.id, words[0].openCompoundId)
        assertEquals("...", words[0].gloss)
        assertEquals("other occurrence", words[2].contextGloss)
        assertEquals(compound.id, words[2].openCompoundId)

        corpusController.updateContextGloss(graph, text.id, CorpusController.ContextGlossParams(0, ""))
        assertEquals(null, text.words.first().contextGloss)
        assertEquals(compound, text.words.first().openCompound)
    }

    @Test
    fun contextGlossAssociatesSingleCandidate() {
        val word = graph.addWord("laurie", fixture.q, "golden")
        val text = graph.addCorpusText("laurie", "Test", fixture.q)

        corpusController.updateContextGloss(graph, text.id, CorpusController.ContextGlossParams(0, "goldenly"))

        assertEquals(word, text.wordByIndex(0))
        assertEquals("goldenly", text.words.single().contextGloss)
    }

    @Test
    fun contextGlossRejectsUndefinedAmbiguousAndInvalidWords() {
        graph.addWord("laurie", fixture.q, "golden")
        graph.addWord("laurie", fixture.q, "other gloss")
        val text = graph.addCorpusText("laurie unknown", "Test", fixture.q)
        val service = mock(GraphService::class.java)
        given(service.resolveGraph("test")).willReturn(graph)
        val mvc = MockMvcBuilders.standaloneSetup(corpusController)
            .setCustomArgumentResolvers(GraphArgumentResolver(service)).build()

        for (index in listOf(0, 1, -1, 2)) {
            mvc.perform(post("/test/corpus/text/${text.id}/contextGloss")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"index":$index,"contextGloss":"test"}"""))
                .andExpect(status().isBadRequest)
        }
        assertTrue(text.words.isEmpty())
    }

    @Test
    fun consistencyIssues() {
        val corpusText = graph.addCorpusText("alda", "Test text", fixture.q)
        val word = graph.findOrAddWord("elen", fixture.q, "star")
        corpusText.associateWord(0, word)

        val textJson = corpusController.textJson(graph, corpusText.id)

        assertEquals(listOf(
            "Corpus word text 'alda' doesn't match word text 'elen', index 0"
        ), textJson.consistencyIssues)
    }

    @Test
    fun candidateUrlKey() {
        val corpusParams = CorpusController.CorpusTextParams(text = "^m^mur-si")
        val corpusTextViewModel = corpusController.newText(graph, "q", corpusParams)

        val word = graph.findOrAddWord("^m^mur-si", fixture.q, "Mursi")
        val textJson = corpusController.textJson(graph, corpusTextViewModel.id)
        assertEquals("mmur-si", textJson.lines.single().words[0].wordUrlKey)
        assertEquals(word.id, textJson.lines.single().words[0].wordId)
    }

    @Test
    fun remapTranslationAnchorAfterTextEdit() {
        val corpusParams = CorpusController.CorpusTextParams(text = "Ai laurie lantar")
        val corpusText = corpusController.newText(graph, "q", corpusParams)
        val translationController = TranslationController()
        translationController.addTranslation(
            graph,
            TranslationController.TranslationParams(corpusText.id, "Golden fall", "", 1)
        )

        corpusController.editText(
            graph,
            corpusText.id,
            CorpusController.CorpusTextParams(text = "Ai lantar")
        )

        val updated = corpusController.textJson(graph, corpusText.id)
        assertEquals(1, updated.translations.size)
        assertEquals(1, updated.translations[0].anchorStartIndex)
        assertEquals(2, updated.translations[0].anchorEndIndex)
    }

    @Test
    fun unanchorTranslationWhenRangeDeleted() {
        val corpusParams = CorpusController.CorpusTextParams(text = "Ai laurie")
        val corpusText = corpusController.newText(graph, "q", corpusParams)
        val translationController = TranslationController()
        translationController.addTranslation(
            graph,
            TranslationController.TranslationParams(corpusText.id, "Golden", "", 1)
        )

        corpusController.editText(
            graph,
            corpusText.id,
            CorpusController.CorpusTextParams(text = "Ai")
        )

        val updated = corpusController.textJson(graph, corpusText.id)
        assertEquals(1, updated.translations.size)
        assertEquals(null, updated.translations[0].anchorStartIndex)
        assertEquals(null, updated.translations[0].anchorEndIndex)
    }

    @Test
    fun deleteText() {
        val corpusParams = CorpusController.CorpusTextParams(text = "Ai laurie")
        val corpusText = corpusController.newText(graph, "q", corpusParams)
        corpusController.deleteText(graph, corpusText.id)
        assertEquals(0, graph.allCorpusTexts().count())
    }
}
