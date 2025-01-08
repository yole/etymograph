package ru.yole.etymograph.web

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.yole.etymograph.Link
import ru.yole.etymograph.web.controllers.LinkController
import ru.yole.etymograph.web.controllers.RuleController
import ru.yole.etymograph.web.controllers.WordController

class LinkControllerTest {
    lateinit var fixture: QTestFixture
    lateinit var linkController: LinkController
    lateinit var wordController: WordController

    @Before
    fun setup() {
        fixture = QTestFixture()
        linkController = LinkController()
        wordController = WordController(DictionaryService())
    }

    @Test
    fun testLinkSource() {
        val word1 = fixture.graph.findOrAddWord("abc", fixture.q, "abc")
        val word2 = fixture.graph.findOrAddWord("def", fixture.q, "def")

        linkController.addLink(fixture.graph, LinkController.LinkParams(word1.id, word2.id, Link.Related.id, source = "src"))

        val json = wordController.singleWordJson(fixture.graph, "q", "abc", word1.id)
        assertEquals("src", json.linksFrom.single().words.single().source.single().refText)
        assertEquals("src", json.linksFrom.single().words.single().sourceEditableText)
    }

    @Test
    fun linkWordToRule() {
        val word = fixture.graph.findOrAddWord("abc", fixture.q, "abc")

        val ruleController = RuleController()
        ruleController.newRule(
            fixture.graph,
            RuleController.UpdateRuleParameters(
                "q-pos",
                "q", "q",
                "sound is 't' and end of word:\n- new sound is 'θ'",
            ))

        linkController.addRuleLink(fixture.graph, LinkController.RuleLinkParams(word.id, "q-pos", Link.Related.id))

        val wordViewModel = wordController.singleWordJson(fixture.graph, "q", "abc", word.id)
        assertEquals(0, wordViewModel.linksFrom.size)
        assertEquals(0, wordViewModel.linksTo.size)
        val relatedRuleViewModel = wordViewModel.linkedRules.single()
        assertEquals("q-pos", relatedRuleViewModel.ruleName)

        val rule = fixture.graph.ruleByName("q-pos")!!
        val ruleViewModel = ruleController.rule(fixture.graph, rule.id)
        val linkViewModel = ruleViewModel.linkedWords.single()
        assertEquals("abc", linkViewModel.toWord.text)
    }

    @Test
    fun linkRuleFromBase() {
        val ruleController = RuleController()
        ruleController.newRule(
            fixture.graph,
            RuleController.UpdateRuleParameters(
                "q-pos",
                "q", "q",
                "sound is 't' and end of word:\n- new sound is 'θ'",
            ))

        val baseWord = fixture.graph.findOrAddWord("mbar", fixture.ce, "home")
        val derivedWord = fixture.graph.findOrAddWord("mar", fixture.q, "home")
        val link = fixture.graph.addLink(derivedWord, baseWord, Link.Derived)

        linkController.updateLink(fixture.graph, LinkController.LinkParams(
            fromEntity = baseWord.id,
            toEntity = derivedWord.id,
            linkType = Link.Derived.id,
            ruleNames = "q-pos"
        ))

        assertEquals("q-pos", link.rules.single().name)
    }
}
