package page.yole.etymograph

import org.junit.Assert.*
import org.junit.Test

class CorpusOpenCompoundTest : QBaseTest() {
    private val prefix = q.word("laurie", "golden")
    private val head = q.word("lasse", "leaf")
    private val suffix = q.word("alda", "tree")

    private fun CorpusText.match(index: Int, compound: OpenCompound): CorpusOpenCompoundCandidate =
        findOpenCompoundCandidates(index).single { it.compound.id == compound.id }

    @Test
    fun removesOnlyClickedOccurrenceFromRepeatedCompoundFromAnyComponent() {
        val compound = graph.createOpenCompound(listOf(prefix, head, suffix), 1, "foliage")
        for (clickedIndex in 0..5) {
            val text = q.corpusText("laurie lasse alda laurie lasse alda")
            text.associateOpenCompound(1, compound)
            text.associateOpenCompound(4, compound)
            text.words.forEach { it.contextGloss = "Context ${it.index}" }

            text.removeOpenCompoundAssociation(clickedIndex, compound)

            val removedIndices = if (clickedIndex < 3) listOf(0, 1, 2) else listOf(3, 4, 5)
            assertEquals(removedIndices, text.words.filter { it.openCompound == null }.map { it.index })
            assertEquals(6, text.words.size)
            assertTrue(text.words.all { it.contextGloss == "Context ${it.index}" })
            assertSame(compound, graph.langEntityById(compound.id))
        }
    }

    @Test
    fun removesOnlyClosestLinkedComponentsInCorrectDirection() {
        val compound = graph.createOpenCompound(listOf(prefix, head, suffix), 1)
        val text = q.corpusText("laurie laurie lasse alda alda")
        for ((index, word) in listOf(prefix, prefix, head, suffix, suffix).withIndex()) {
            text.associateWord(index, word, openCompound = compound)
        }

        text.removeOpenCompoundAssociation(2, compound)

        assertEquals(listOf(1, 2, 3), text.words.filter { it.openCompound == null }.map { it.index })
        assertEquals(listOf(0, 4), text.words.filter { it.openCompound != null }.map { it.index })
    }

    @Test
    fun doesNotCrossAnotherHeadWhenRemovingIncompleteOccurrence() {
        val compound = graph.createOpenCompound(listOf(prefix, head, suffix), 1)
        val text = q.corpusText("laurie lasse alda lasse")
        text.associateOpenCompound(1, compound)
        text.associateWord(3, head, openCompound = compound)

        text.removeOpenCompoundAssociation(3, compound)

        assertNull(text.words.single { it.index == 3 }.openCompound)
        assertTrue(text.words.filter { it.index != 3 }.all { it.openCompound === compound })
    }

    @Test
    fun removesAvailableComponentsOfIncompleteOccurrenceWithoutTouchingOthers() {
        val compound = graph.createOpenCompound(listOf(prefix, head, suffix), 1)
        val text = q.corpusText("laurie lasse alda laurie lasse")
        text.associateOpenCompound(1, compound)
        text.associateWord(3, prefix, openCompound = compound)
        text.associateWord(4, head, openCompound = compound)

        text.removeOpenCompoundAssociation(3, compound)

        assertEquals(listOf(3, 4), text.words.filter { it.openCompound == null }.map { it.index })
        assertTrue(text.words.filter { it.index < 3 }.all { it.openCompound === compound })
    }

    @Test
    fun removingDoesNotChooseUnlinkedWordsOrOtherCompounds() {
        val compound = graph.createOpenCompound(listOf(prefix, head), 1)
        val other = graph.createOpenCompound(listOf(prefix), 0)
        val text = q.corpusText("laurie laurie lasse laurie lasse")
        text.associateOpenCompound(2, compound)
        text.associateWord(0, prefix, openCompound = other)
        text.associateWord(3, prefix)
        text.associateWord(4, head, openCompound = compound)

        text.removeOpenCompoundAssociation(4, compound)

        assertTrue(text.words.filter { it.index in listOf(1, 2) }.all { it.openCompound === compound })
        assertSame(other, text.words.single { it.index == 0 }.openCompound)
        assertNull(text.words.single { it.index == 4 }.openCompound)
    }

    @Test
    fun removesDerivedAndTranscribedOccurrencesOnlyFromNearestGroup() {
        val form = q.word("lassi", "leaves")
        val transcription = q.word("lassie", "transcription")
        val rule = q.rule("- no change", addedCategories = ".PL")
        graph.addLink(form, head, Link.Derived, listOf(rule))
        graph.addLink(transcription, form, Link.Transcription)
        val compound = graph.createOpenCompound(listOf(prefix, head), 1, "foliage")
        val text = q.corpusText("laurie lassie laurie lassie")
        text.associateOpenCompound(1, compound)
        text.associateOpenCompound(3, compound)

        text.removeOpenCompoundAssociation(3, compound)

        assertEquals(listOf(2, 3), text.words.filter { it.openCompound == null }.map { it.index })
        assertTrue(text.words.filter { it.index < 2 }.all { it.openCompound === compound })
    }

    @Test
    fun removingRejectsStaleCompoundOrMissingLinkWithoutChanges() {
        val compound = graph.createOpenCompound(listOf(prefix, head), 1)
        val other = graph.createOpenCompound(listOf(prefix, head), 1)
        val text = q.corpusText("laurie lasse")
        text.associateOpenCompound(1, compound)
        assertThrows(IllegalArgumentException::class.java) { text.removeOpenCompoundAssociation(1, other) }
        assertThrows(IllegalArgumentException::class.java) { text.removeOpenCompoundAssociation(99, compound) }
        assertTrue(text.words.all { it.openCompound === compound })
        text.removeOpenCompoundAssociation(1, compound)
        assertThrows(IllegalArgumentException::class.java) { text.removeOpenCompoundAssociation(1, compound) }
    }

    @Test
    fun findsAndAssociatesUnlinkedWordsFromEitherComponent() {
        val compound = graph.createOpenCompound(listOf(prefix, head), 1, "foliage")
        val text = q.corpusText("laurie lasse")
        for (index in 0..1) {
            assertEquals(listOf(0, 1), text.match(index, compound).associations.map { it.index })
        }
        assertTrue(text.words.isEmpty())

        text.associateOpenCompound(0, compound)
        assertEquals(listOf(prefix.id, head.id), text.words.map { it.word.id })
        assertTrue(text.words.all { it.openCompound === compound })
        assertEquals(listOf("...", "foliage"), text.mapToLines().single().corpusWords.map { it.gloss })
    }

    @Test
    fun requiresEveryComponentAndIncludesOnlyCompoundsContainingClickedWord() {
        val prefixCompound = graph.createOpenCompound(listOf(prefix, head), 1)
        val suffixCompound = graph.createOpenCompound(listOf(head, suffix), 0)
        val absent = q.word("mir", "jewel")
        graph.createOpenCompound(listOf(head, absent), 0)
        graph.createOpenCompound(listOf(prefix, suffix), 1)
        val text = q.corpusText("laurie lasse alda")

        assertEquals(listOf(prefixCompound.id, suffixCompound.id),
            text.findOpenCompoundCandidates(1).map { it.compound.id })
    }

    @Test
    fun rejectsComponentsOnWrongSideOfHead() {
        val prefixCompound = graph.createOpenCompound(listOf(prefix, head), 1)
        val suffixCompound = graph.createOpenCompound(listOf(head, suffix), 0)
        val text = q.corpusText("alda lasse laurie")
        for (index in 0..2) {
            assertTrue(text.findOpenCompoundCandidates(index).none {
                it.compound.id == prefixCompound.id || it.compound.id == suffixCompound.id
            })
        }
    }

    @Test
    fun preservesComponentOrderWithInterveningWordsAndLineBreaks() {
        val compound = graph.createOpenCompound(listOf(prefix, head, suffix), 1)
        val text = q.corpusText("laurie ai lasse\n---\n1 # alda")
        for (index in listOf(0, 2, 3)) {
            assertEquals(listOf(0, 2, 3), text.match(index, compound).associations.map { it.index })
        }

        val twoPrefixes = graph.createOpenCompound(listOf(prefix, suffix, head), 2)
        assertTrue(q.corpusText("alda laurie lasse").findOpenCompoundCandidates(2).none {
            it.compound.id == twoPrefixes.id
        })
    }

    @Test
    fun choosesClosestOccurrencesAndKeepsClickedOccurrenceFixed() {
        val compound = graph.createOpenCompound(listOf(prefix, head, suffix), 1)
        val text = q.corpusText("laurie ai laurie lasse alda ai alda")
        assertEquals(listOf(2, 3, 4), text.match(3, compound).associations.map { it.index })
        assertEquals(listOf(0, 3, 4), text.match(0, compound).associations.map { it.index })
        assertEquals(listOf(2, 3, 6), text.match(6, compound).associations.map { it.index })
    }

    @Test
    fun choosesClosestHeadInRequiredDirection() {
        val compound = graph.createOpenCompound(listOf(prefix, head), 1)
        val text = q.corpusText("laurie lasse ai laurie lasse lasse")
        assertEquals(listOf(3, 4), text.match(3, compound).associations.map { it.index })
        assertEquals(listOf(0, 1), text.match(0, compound).associations.map { it.index })
    }

    @Test
    fun skipsOccurrencesAssociatedWithOtherCompounds() {
        val compound = graph.createOpenCompound(listOf(prefix, head), 1)
        val other = graph.createOpenCompound(listOf(prefix), 0)
        val text = q.corpusText("laurie laurie lasse")
        text.associateWord(1, prefix, openCompound = other)

        assertEquals(listOf(0, 2), text.match(2, compound).associations.map { it.index })
        assertTrue(text.findOpenCompoundCandidates(1).none { it.compound.id == compound.id })
        text.associateWord(0, prefix, openCompound = other)
        assertTrue(text.findOpenCompoundCandidates(2).none { it.compound.id == compound.id })
        assertThrows(IllegalArgumentException::class.java) { text.associateOpenCompound(2, compound) }
        assertNull(text.wordByIndex(2))
        assertTrue(text.words.all { it.openCompound === other })
    }

    @Test
    fun acceptsExistingAssociationsToSameCompoundAndPreservesContextGloss() {
        val compound = graph.createOpenCompound(listOf(prefix, head), 1)
        val text = q.corpusText("laurie lasse")
        text.associateWord(0, prefix, "context gloss", compound)
        text.associateOpenCompound(1, compound)

        assertEquals("context gloss", text.words.first { it.index == 0 }.contextGloss)
        assertTrue(text.words.all { it.openCompound === compound })
        assertEquals(compound.id, text.findOpenCompoundCandidates(1).single().compound.id)
    }

    @Test
    fun matchesDerivedVariationAndTranscriptionChainsAndAppliesCategories() {
        val prefixVariant = q.word("laure", "golden variant")
        val plural = q.word("lassi", "leaves")
        val variant = q.word("lassie", "variant")
        val transcription = q.word("lassiea", "transcription")
        val rule = q.rule("- no change", addedCategories = ".PL")
        graph.addLink(prefixVariant, prefix, Link.Variation)
        graph.addLink(plural, head, Link.Derived, listOf(rule))
        graph.addLink(variant, plural, Link.Variation)
        graph.addLink(transcription, variant, Link.Transcription)
        val compound = graph.createOpenCompound(listOf(prefix, head), 1, "foliage")
        val text = q.corpusText("laure lassiea")

        assertEquals(listOf(prefixVariant.id, transcription.id),
            text.match(1, compound).associations.map { it.word.id })
        text.associateOpenCompound(1, compound)
        assertEquals(listOf("...", "foliage.PL"), text.mapToLines().single().corpusWords.map { it.gloss })
    }

    @Test
    fun respectsExplicitWordAssociationsAndDoesNotReuseAnOccurrence() {
        val homonym = q.word("laurie", "different word")
        val compound = graph.createOpenCompound(listOf(prefix, head), 1)
        val text = q.corpusText("laurie lasse")
        assertEquals(compound.id, text.findOpenCompoundCandidates(1).single().compound.id)
        text.associateWord(0, homonym)
        assertTrue(text.findOpenCompoundCandidates(1).isEmpty())

        val second = q.word("laurie", "second homonym")
        val homonymCompound = graph.createOpenCompound(listOf(prefix, second), 1)
        assertTrue(q.corpusText("laurie").findOpenCompoundCandidates(0).none { it.compound.id == homonymCompound.id })
    }

    @Test
    fun rejectsUnrelatedWordsAndInvalidIndices() {
        val other = q.word("laure", "other")
        graph.addLink(other, prefix, Link.Related)
        val compound = graph.createOpenCompound(listOf(prefix, head), 1)
        val text = q.corpusText("laure lasse")
        assertTrue(text.findOpenCompoundCandidates(1).none { it.compound.id == compound.id })
        assertThrows(IllegalArgumentException::class.java) { text.findOpenCompoundCandidates(-1) }
        assertThrows(IllegalArgumentException::class.java) { text.findOpenCompoundCandidates(2) }
    }
}
