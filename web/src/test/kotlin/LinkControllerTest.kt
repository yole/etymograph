package ru.yole.etymograph.web

import org.junit.Assert
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
        Assert.assertEquals("src", json.linksFrom.single().words.single().source.single().refText)
        Assert.assertEquals("src", json.linksFrom.single().words.single().sourceEditableText)
    }
}
