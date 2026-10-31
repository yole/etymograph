package page.yole.etymograph.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import page.yole.etymograph.InMemoryGraph
import page.yole.etymograph.web.controllers.ImportController

class ImportControllerTest {
    private lateinit var graph: InMemoryGraph
    private lateinit var mvc: MockMvc

    @Before
    fun setup() {
        graph = InMemoryGraph()
        val service = mock(GraphService::class.java)
        given(service.resolveGraph("test")).willReturn(graph)
        mvc = MockMvcBuilders.standaloneSetup(ImportController())
            .setCustomArgumentResolvers(GraphArgumentResolver(service)).build()
    }

    private fun importFile(content: String) = mvc.perform(
        multipart("/test/import/tlhDig")
            .file(MockMultipartFile("file", "tablet.xml", "application/xml", content.toByteArray(Charsets.UTF_8)))
    )

    @Test
    fun importsTextAndReturnsMessagesAndWordReferences() {
        val hittite = graph.addLanguage("Hittite", "Hitt")
        val xml = """
            <text>
                <AOHeader><docID>Test tablet</docID></AOHeader>
                <body><div1><text>
                    <lb lnr="1"/>
                    <w trans="arḫa" mrp0sel="2" mrp2="arḫa@weg@@ ADV@">ar-ḫa</w>
                    <w>x</w>
                </text></div1></body>
            </text>
        """.trimIndent()

        importFile(xml)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.messages[0]").value("Skipping damaged word x"))
            .andExpect(jsonPath("$.newWords.length()").value(1))
            .andExpect(jsonPath("$.newWords[0].text").value("arḫa"))
            .andExpect(jsonPath("$.newWords[0].gloss").value("weg"))
            .andExpect(jsonPath("$.newWords[0].language").value("Hitt"))
            .andExpect(jsonPath("$.newWords[0].id").isNumber)

        val text = graph.corpusTextsInLanguage(hittite).single()
        assertEquals("Test tablet", text.title)
        assertEquals("1# ar-ḫa x", text.text.trim())
        assertEquals("ar-ḫa", text.words.first().word.text)
    }

    @Test
    fun rejectsEmptyUpload() {
        importFile("").andExpect(status().isBadRequest)
        assertTrue(graph.allCorpusTexts().none())
    }

    @Test
    fun rejectsMissingHittiteLanguage() {
        importFile("<text/>").andExpect(status().isBadRequest)
        assertTrue(graph.allCorpusTexts().none())
    }

    @Test
    fun rejectsMissingUpload() {
        graph.addLanguage("Hittite", "Hitt")
        mvc.perform(multipart("/test/import/tlhDig")).andExpect(status().isBadRequest)
        assertTrue(graph.allCorpusTexts().none())
    }

    @Test
    fun rejectsInvalidXml() {
        graph.addLanguage("Hittite", "Hitt")
        importFile("<text>").andExpect(status().isBadRequest)
        assertTrue(graph.allCorpusTexts().none())
    }
}
