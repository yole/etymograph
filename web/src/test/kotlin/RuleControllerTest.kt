package ru.yole.etymograph.web

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.springframework.web.server.ResponseStatusException
import ru.yole.etymograph.*
import ru.yole.etymograph.web.controllers.RuleController

class RuleControllerTest {
    private lateinit var fixture: QTestFixture
    private lateinit var ruleController: RuleController
    private lateinit var graph: GraphRepository
    private lateinit var q: Language
    private lateinit var ce: Language
    private lateinit var aq: Language

    @Before
    fun setup() {
        fixture = QTestFixture()
        q = fixture.q
        ce = fixture.ce
        aq = Language("Ancient Quenya", "AQ").also { fixture.graph.addLanguage(it) }

        ruleController = RuleController()
        graph = fixture.graph
    }

    @Test
    fun testGrammaticalCategories() {
        q.grammaticalCategories.add(
            WordCategory("Case", listOf("N"),
                listOf(WordCategoryValue("Genitive", "GEN"))))

        val rule = graph.addRule("q-gen", fixture.q, fixture.q,
            RuleLogic(emptyList(), emptyList(), emptyList()), ".GEN")

        val ruleVM = ruleController.rule(fixture.graph, rule.id)
        assertEquals("Case: Genitive", ruleVM.addedCategoryDisplayNames)
    }

    @Test
    fun testGrammaticalCategoriesExtractNumber() {
        q.grammaticalCategories.add(
            WordCategory("Person", listOf("V"),
                listOf(WordCategoryValue("1st person", "1"))))
        q.grammaticalCategories.add(
            WordCategory("Number", listOf("V"),
                listOf(WordCategoryValue("Singular", "SG"))))

        val rule = graph.addRule("q-1sg", fixture.q, fixture.q,
            RuleLogic(emptyList(), emptyList(), emptyList()), ".1SG")

        val ruleVM = ruleController.rule(fixture.graph, rule.id)
        assertEquals("Person: 1st person, Number: Singular", ruleVM.addedCategoryDisplayNames)
    }

    @Test
    fun testEmptyToPOS() {
        ruleController.newRule(
            fixture.graph,
            RuleController.UpdateRuleParameters(
            "q-pos",
            "q", "q",
            "- no change",
            toPOS = ""
        ))

        val rule = graph.ruleByName("q-pos")
        assertNull(rule!!.toPOS)
    }

    @Test
    fun validateFromPOS() {
        assertBadRequest("Unknown POS 'X'") {
            ruleController.newRule(
                fixture.graph,
                RuleController.UpdateRuleParameters(
                    "q-pos",
                    "q", "q",
                    "- no change",
                    fromPOS = "X"
                ))
        }
    }

    @Test
    fun uniqueRuleName() {
        ruleController.newRule(
            fixture.graph,
            RuleController.UpdateRuleParameters(
                "q-pos",
                "q", "q",
                "- no change"
            ))

        assertThrows(ResponseStatusException::class.java) {
            ruleController.newRule(
                fixture.graph,
                RuleController.UpdateRuleParameters(
                    "q-pos",
                    "q", "q",
                    "- no change"
                ))
        }
    }

    @Test
    fun newSequence() {
        val rule = graph.addRule("q-gen", q, q,
            RuleLogic(emptyList(), emptyList(), emptyList()))

        ruleController.newSequence(
            fixture.graph,
            RuleController.UpdateSequenceParams(
                "ce-to-q",
                "ce",
                "q",
                rule.name
            )
        )

        val seq = graph.ruleSequencesForLanguage(fixture.q).single()

        val ruleList = ruleController.rules(fixture.graph, "q")
        assertEquals(1, ruleList.ruleGroups.size)
        assertEquals("Phonetics: ce-to-q", ruleList.ruleGroups[0].groupName)
        assertEquals(seq.id, ruleList.ruleGroups[0].sequenceId)
    }

    @Test
    fun newSequenceOptional() {
        val rule = graph.addRule("q-gen", fixture.q, fixture.q,
            RuleLogic(emptyList(), emptyList(), emptyList()))

        ruleController.newSequence(
            fixture.graph,
            RuleController.UpdateSequenceParams(
                "ce-to-q",
                "ce",
                "q",
                rule.name + "?"
            )
        )

        val seq = graph.ruleSequencesForLanguage(fixture.q).single()

        val ruleList = ruleController.rules(fixture.graph, "q")
        assertEquals(1, ruleList.ruleGroups.size)
        assertEquals("Phonetics: ce-to-q", ruleList.ruleGroups[0].groupName)
        assertEquals(seq.id, ruleList.ruleGroups[0].sequenceId)
        assertTrue(ruleList.ruleGroups[0].rules[0].optional)
    }

    @Test
    fun editSequence() {
        val rule = graph.addRule("q-gen", fixture.q, fixture.q,
            RuleLogic(emptyList(), emptyList(), emptyList()))
        val seq = graph.addRuleSequence("ce-to-q", fixture.ce, fixture.q, listOf(rule.step()))

        val rule2 = graph.addRule("q-acc", fixture.q, fixture.q,
            RuleLogic(emptyList(), emptyList(), emptyList()))

        ruleController.updateSequence(
            fixture.graph,
            seq.id,
            RuleController.UpdateSequenceParams(
                "ce-to-q",
                "ce",
                "q",
                "${rule.name}\n${rule2.name}"
            )
        )

        val ruleList = ruleController.rules(fixture.graph, "q")
        assertEquals(1, ruleList.ruleGroups.size)
        assertEquals(2, ruleList.ruleGroups[0].rules.size)
    }

    @Test
    fun applySequence() {
        val seq = fixture.setupRuleSequence()
        val w1 = graph.findOrAddWord("am", fixture.ce, null)
        val w2 = graph.findOrAddWord("an", fixture.q, null)
        val link = graph.addLink(w2, w1, Link.Origin)

        val result = ruleController.applySequence(fixture.graph, seq.id, RuleController.ApplySequenceParams(w2.id, w1.id))
        assertEquals(1, result.ruleIds.size)
        assertEquals(1, link.rules.size)
    }

    @Test
    fun nestedSequence() {
        val ceRule = ruleController.newRule(
            fixture.graph,
            RuleController.UpdateRuleParameters(
                "ce-p-f",
                "ce", "ce",
                "sound is 'p':\n- new sound is 'f'"
            )
        )
        val ceSequence = graph.addRuleSequence("ce-sequence", fixture.ce, fixture.ce,
            listOf(graph.ruleByName(ceRule.name)!!.step()))

        val qRule = ruleController.newRule(
            fixture.graph,
            RuleController.UpdateRuleParameters(
                "q-final-consonant",
                "q", "q",
                "end of word and sound is 'm':\n- new sound is 'n'"
            )
        )

        ruleController.newSequence(
            fixture.graph,
            RuleController.UpdateSequenceParams(
                "ce-to-q",
                "ce",
                "q",
                "sequence: ${ceSequence.name}\n${qRule.name}"
            )
        )
        val seq = graph.ruleSequencesForLanguage(fixture.q).single()
        assertEquals(ceSequence.id, seq.steps[0].ruleId)
        assertEquals(qRule.id, seq.steps[1].ruleId)

        val rules = ruleController.rules(fixture.graph, "q")
        val group = rules.ruleGroups.single()
        assertEquals(2, group.rules.size)
    }

    @Test
    fun expectedWordOrtho() {
        fixture.q.phonemes = listOf(Phoneme(-1, listOf("th"), "θ", setOf("consonant")))

        ruleController.newRule(
            fixture.graph,
            RuleController.UpdateRuleParameters(
                "q-pos",
                "q", "q",
                "sound is 't' and end of word:\n- new sound is 'θ'",
            ))
        val rule = graph.ruleByName("q-pos")!!

        val word1 = graph.findOrAddWord("ait", fixture.q, "")
        val word2 = graph.findOrAddWord("aith", fixture.q, "")
        graph.addLink(word2, word1, Link.Derived, listOf(rule))

        val ruleViewModel = ruleController.rule(fixture.graph, rule.id)
        val example = ruleViewModel.branches.single().examples.single()
        assertNull(example.expectedWord)
    }

    @Test
    fun derivationsForChainedSequence() {
        val (ceSeq, ceWord, qWord) = setupChainedSequenceDerivation("veiwei")

        val derivationViewModel = ruleController.sequenceDerivations(graph, ceSeq.id)
        assertEquals(1, derivationViewModel.derivations.size)
        val d = derivationViewModel.derivations.single()
        assertEquals(ceWord.id, d.baseWord.id)
        assertEquals(qWord.id, d.derivation.word.id)
        assertEquals(2, d.derivation.ruleIds.size)
        assertNull(d.expectedWord)
    }

    private fun setupChainedSequenceDerivation(qWordText: String): Triple<RuleSequence, Word, Word> {
        val qAiE = graph.rule(
            "sound is 'a' and next sound is 'i':\n- new sound is 'e'",
            name = "q-ai-e",
            fromLanguage = ce,
            toLanguage = aq
        )
        val aqSeq = graph.addRuleSequence("ce-aq", ce, aq, listOf(qAiE.step()))
        val qWV = graph.rule(
            "beginning of word and sound is 'w':\n- new sound is 'v'",
            name = "q-w-v",
            fromLanguage = aq,
            toLanguage = q
        )
        val qSeq = graph.addRuleSequence("aq-q", aq, q, listOf(qWV.step()))

        val ceSeq = graph.addRuleSequence("ce-q", ce, q, listOf(aqSeq.step(), qSeq.step()))

        val ceWord = graph.addWord("waiwai", language = ce, gloss = "w")
        val aqWord = graph.addWord("weiwei", language = aq, gloss = "w")
        val aqLink = graph.addLink(aqWord, ceWord, Link.Origin)
        graph.applyRuleSequence(aqLink, aqSeq)

        val qWord = graph.addWord(qWordText, language = q, gloss = "w")
        val qLink = graph.addLink(qWord, aqWord, Link.Origin)
        graph.applyRuleSequence(qLink, qSeq)
        return Triple(ceSeq, ceWord, qWord)
    }

    @Test
    fun derivationsForChainedSequenceExpectedRule() {
        val (ceSeq, ceWord, qWord) = setupChainedSequenceDerivation("veivei")

        val derivationViewModel = ruleController.sequenceDerivations(graph, ceSeq.id)
        assertEquals(1, derivationViewModel.derivations.size)
        val d = derivationViewModel.derivations.single()
        assertEquals(ceWord.id, d.baseWord.id)
        assertEquals(qWord.id, d.derivation.word.id)
        assertEquals(2, d.derivation.ruleIds.size)
        assertEquals("veiwei", d.expectedWord)
        assertEquals("w -> v", d.singlePhonemeDifference)
    }

    @Test
    fun derivationsForChainedSequenceVariants() {
        val (ceSeq, ceWord, qWord) = setupChainedSequenceDerivation("veivei")
        val qWordVariant = graph.addWord("veiwei", language = q, gloss = null)
        graph.addLink(qWordVariant, qWord, Link.Variation)

        val derivationViewModel = ruleController.sequenceDerivations(graph, ceSeq.id)
        val d = derivationViewModel.derivations.single()
        assertNull(d.expectedWord)
    }
}
