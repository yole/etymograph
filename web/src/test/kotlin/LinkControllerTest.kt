package ru.yole.etymograph.web

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.yole.etymograph.Link

class LinkControllerTest {
    @Test
    fun testLinkSource() {
        val fixture = QTestFixture()

        val linkController = LinkController(fixture.graphService)
        val word1 = fixture.graph.findOrAddWord("abc", fixture.q, "abc")
        val word2 = fixture.graph.findOrAddWord("def", fixture.q, "def")

        linkController.addLink(LinkController.LinkParams(word1.id, word2.id, Link.Related.id, source = "src"))

        val wordController = WordController(fixture.graphService)
        val json = wordController.singleWordJson("q", "abc", word1.id)
        assertEquals("src", json.linksFrom.single().words.single().source.single().refText)
        assertEquals("src", json.linksFrom.single().words.single().sourceEditableText)
    }

    @Test
    fun linkWordToRule() {
        val fixture = QTestFixture()

        val linkController = LinkController(fixture.graphService)
        val word = fixture.graph.findOrAddWord("abc", fixture.q, "abc")

        val ruleController = RuleController(fixture.graphService)
        ruleController.newRule(
            RuleController.UpdateRuleParameters(
                "q-pos",
                "q", "q",
                "sound is 't' and end of word:\n- new sound is 'θ'",
            ))

        linkController.addRuleLink(LinkController.RuleLinkParams(word.id, "q-pos", Link.Related.id))

        val wordController = WordController(fixture.graphService)
        val wordViewModel = wordController.singleWordJson("q", "abc", word.id)
        assertEquals(0, wordViewModel.linksFrom.size)
        assertEquals(0, wordViewModel.linksTo.size)
        val relatedRuleViewModel = wordViewModel.linkedRules.single()
        assertEquals("q-pos", relatedRuleViewModel.ruleName)

        val rule = fixture.graphService.resolveRule("q-pos")
        val ruleViewModel = ruleController.rule(rule.id)
        val linkViewModel = ruleViewModel.linkedWords.single()
        assertEquals("abc", linkViewModel.toWord.text)
    }

    @Test
    fun linkRuleFromBase() {
        val fixture = QTestFixture()
        val ruleController = RuleController(fixture.graphService)
        ruleController.newRule(
            RuleController.UpdateRuleParameters(
                "q-pos",
                "q", "q",
                "sound is 't' and end of word:\n- new sound is 'θ'",
            ))

        val linkController = LinkController(fixture.graphService)
        val baseWord = fixture.graph.findOrAddWord("mbar", fixture.ce, "home")
        val derivedWord = fixture.graph.findOrAddWord("mar", fixture.q, "home")
        val link = fixture.graph.addLink(derivedWord, baseWord, Link.Derived, emptyList(), emptyList(), null)

        linkController.updateLink(LinkController.LinkParams(
            fromEntity = baseWord.id,
            toEntity = derivedWord.id,
            linkType = Link.Derived.id,
            ruleNames = "q-pos"
        ))

        assertEquals("q-pos", link.rules.single().name)
    }
}
