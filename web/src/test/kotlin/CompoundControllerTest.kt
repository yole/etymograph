package page.yole.etymograph.web

import org.junit.Assert.*
import org.junit.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.mockito.Mockito.mock
import org.mockito.BDDMockito.given
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.server.ResponseStatusException
import page.yole.etymograph.Link
import page.yole.etymograph.associateOpenCompound
import page.yole.etymograph.removeOpenCompoundAssociation
import page.yole.etymograph.web.controllers.CompoundController
import page.yole.etymograph.web.controllers.CompoundController.OpenCompoundParams
import page.yole.etymograph.web.controllers.WordController

class CompoundControllerTest {
    private val fixture = QTestFixture()
    private val graph = fixture.graph
    private val controller = CompoundController()
    private val fara = graph.addWord("fara", fixture.q, "hunt")
    private val mir = graph.addWord("mir", fixture.q, "jewel")
    private val params = OpenCompoundParams(listOf(fara.id, mir.id), 1,
        "hunting jewel", "A jewel used for hunting", "Test source", "Notes")

    @Test
    fun attestationsIncludeEachLinkedCorpusTextOnce() {
        val created = controller.createOpenCompound(graph, params)
        val compound = graph.allOpenCompounds().single()
        val first = graph.addCorpusText("fara mir fara mir", "First text", fixture.q)
        first.associateOpenCompound(1, compound)
        first.associateOpenCompound(3, compound)
        val derived = graph.addWord("miri", fixture.q, "jewels")
        graph.addLink(derived, mir, Link.Derived)
        val untitled = graph.addCorpusText("fara miri", null, fixture.q)
        untitled.associateOpenCompound(1, compound)
        graph.addCorpusText("fara mir", "Unlinked text", fixture.q)
        val otherCompound = graph.createOpenCompound(listOf(fara, mir), 1, "Other compound")
        graph.addCorpusText("fara mir", "Other compound text", fixture.q).associateOpenCompound(1, otherCompound)

        val attestations = controller.openCompound(graph, created.id).attestations
        assertEquals(listOf(first.id, untitled.id), attestations.map { it.textId })
        assertEquals(listOf("First text", "fara miri"), attestations.map { it.textTitle })
        val word = WordController(TestDictionaryService()).singleWordJson(graph, "q", mir.text, mir.id)
        assertEquals(attestations, word.openCompounds.single { it.id == compound.id }.attestations)

        first.removeOpenCompoundAssociation(1, compound)
        assertEquals(listOf(first.id, untitled.id), controller.openCompound(graph, created.id).attestations.map { it.textId })
        first.removeOpenCompoundAssociation(3, compound)
        assertEquals(listOf(untitled.id), controller.openCompound(graph, created.id).attestations.map { it.textId })
        untitled.removeOpenCompoundAssociation(1, compound)
        assertTrue(controller.openCompound(graph, created.id).attestations.isEmpty())
    }

    @Test
    fun httpOpenCompoundCrudRequiresHead() {
        val graphService = mock(GraphService::class.java)
        given(graphService.resolveGraph("test")).willReturn(graph)
        val mvc = MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(GraphArgumentResolver(graphService)).build()

        for (body in listOf(
            """{"componentIds":[${fara.id},${mir.id}]}""",
            """{"componentIds":[${fara.id},${mir.id}],"headIndex":null}""",
            """{"componentIds":[${fara.id},${mir.id},${mir.id}],"headIndex":0}"""
        )) {
            mvc.perform(post("/test/compound/open").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest)
        }
        assertTrue(graph.allOpenCompounds().isEmpty())

        mvc.perform(post("/test/compound/open").contentType(MediaType.APPLICATION_JSON)
            .content("""{"componentIds":[${fara.id},${mir.id}],"headIndex":1,"gloss":"hunting jewel"}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.headIndex").value(1))
            .andExpect(jsonPath("$.gloss").value("hunting jewel"))
        mvc.perform(get("/test/compound/open"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].headIndex").value(1))

        val id = graph.allOpenCompounds().single().id
        mvc.perform(get("/test/compound/open/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.components[1].id").value(mir.id))
        mvc.perform(post("/test/compound/open/$id").contentType(MediaType.APPLICATION_JSON)
            .content("""{"componentIds":[${fara.id},${mir.id}]}"""))
            .andExpect(status().isBadRequest)
        assertEquals(1, graph.allOpenCompounds().single().headIndex)
        mvc.perform(post("/test/compound/open/$id").contentType(MediaType.APPLICATION_JSON)
            .content("""{"componentIds":[${mir.id},${fara.id}],"headIndex":0,"notes":"Updated"}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.headIndex").value(0))
            .andExpect(jsonPath("$.notes").value("Updated"))
        mvc.perform(post("/test/compound/open/$id/delete"))
            .andExpect(status().isOk)
        mvc.perform(get("/test/compound/open/$id"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun repeatedComponentsAreBadRequest() {
        val duplicateParams = params.copy(componentIds = listOf(fara.id, mir.id, mir.id), headIndex = 0)
        val createError = assertThrows(ResponseStatusException::class.java) {
            controller.createOpenCompound(graph, duplicateParams)
        }
        assertEquals(HttpStatus.BAD_REQUEST, createError.statusCode)
        assertEquals("An open compound cannot contain the same word more than once", createError.reason)
        assertTrue(graph.allOpenCompounds().isEmpty())

        val created = controller.createOpenCompound(graph, params)
        val updateError = assertThrows(ResponseStatusException::class.java) {
            controller.editOpenCompound(graph, created.id, duplicateParams.copy(gloss = "Changed gloss"))
        }
        assertEquals(HttpStatus.BAD_REQUEST, updateError.statusCode)
        assertEquals(created.copy(source = emptyList()),
            controller.openCompound(graph, created.id).copy(source = emptyList()))
    }

    @Test
    fun openCompoundCrud() {
        val created = controller.createOpenCompound(graph, params)
        assertEquals(params.componentIds, created.components.map { it.id })
        assertEquals(1, created.headIndex)
        assertEquals(params.gloss, created.gloss)
        assertEquals(params.fullGloss, created.fullGloss)
        assertEquals(params.source, created.sourceEditableText)
        assertEquals(params.notes, created.notes)
        assertEquals(created.copy(source = emptyList()),
            controller.openCompound(graph, created.id).copy(source = emptyList()))
        assertEquals(listOf(created.id), controller.openCompounds(graph).map { it.id })

        val edited = controller.editOpenCompound(graph, created.id,
            params.copy(componentIds = listOf(mir.id, fara.id), headIndex = 0,
                gloss = "new gloss", fullGloss = "new full gloss", source = "new source", notes = "new notes"))
        assertEquals(listOf(mir.id, fara.id), edited.components.map { it.id })
        assertEquals(0, edited.headIndex)
        assertEquals("new gloss", edited.gloss)
        assertEquals("new full gloss", edited.fullGloss)
        assertEquals("new source", edited.sourceEditableText)
        assertEquals("new notes", edited.notes)

        val cleared = controller.editOpenCompound(graph, created.id,
            OpenCompoundParams(listOf(mir.id), 0, " ", " ", " ", " "))
        assertNull(cleared.gloss)
        assertNull(cleared.fullGloss)
        assertNull(cleared.notes)
        assertTrue(cleared.source.isEmpty())

        controller.deleteOpenCompound(graph, created.id)
        assertTrue(controller.openCompounds(graph).isEmpty())
        assertNull(graph.langEntityById(created.id))
        assertSame(mir, graph.wordById(mir.id))
    }

    @Test
    fun invalidHeadIsBadRequestAndDoesNotCreateEntity() {
        for (invalid in listOf(params.copy(componentIds = emptyList()),
            params.copy(headIndex = -1), params.copy(headIndex = 2))) {
            val exception = assertThrows(ResponseStatusException::class.java) {
                controller.createOpenCompound(graph, invalid)
            }
            assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        }
        assertTrue(graph.allOpenCompounds().isEmpty())
    }

    @Test
    fun failedUpdateDoesNotChangeEntity() {
        val created = controller.createOpenCompound(graph, params)
        for (invalid in listOf(params.copy(headIndex = 2),
            params.copy(componentIds = listOf(mir.id, -1)))) {
            assertThrows(ResponseStatusException::class.java) {
                controller.editOpenCompound(graph, created.id, invalid)
            }
            assertEquals(created.copy(source = emptyList()),
                controller.openCompound(graph, created.id).copy(source = emptyList()))
        }
    }

    @Test
    fun missingComponentsAreNotFound() {
        val exception = assertThrows(ResponseStatusException::class.java) {
            controller.createOpenCompound(graph, params.copy(componentIds = listOf(fara.id, -1)))
        }
        assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)
        assertTrue(graph.allOpenCompounds().isEmpty())
    }

    @Test
    fun unknownOrWrongEntityTypeIsNotFound() {
        for (id in listOf(-1, fara.id)) {
            val readException = assertThrows(ResponseStatusException::class.java) {
                controller.openCompound(graph, id)
            }
            assertEquals(HttpStatus.NOT_FOUND, readException.statusCode)
            val updateException = assertThrows(ResponseStatusException::class.java) {
                controller.editOpenCompound(graph, id, params)
            }
            assertEquals(HttpStatus.NOT_FOUND, updateException.statusCode)
            val deleteException = assertThrows(ResponseStatusException::class.java) {
                controller.deleteOpenCompound(graph, id)
            }
            assertEquals(HttpStatus.NOT_FOUND, deleteException.statusCode)
        }
    }
}
