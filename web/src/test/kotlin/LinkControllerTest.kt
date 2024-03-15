package ru.yole.etymograph.web

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.yole.etymograph.Link

class LinkControllerTest {
    @Test
    fun testLinkSource() {
        val fixture = QTestFixture()

        val linkController = LinkController(fixture.graphService)
        val word1 = fixture.repo.findOrAddWord("abc", fixture.q, "abc")
        val word2 = fixture.repo.findOrAddWord("def", fixture.q, "def")

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
        val word = fixture.repo.findOrAddWord("abc", fixture.q, "abc")

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
}
