package page.yole.etymograph

import org.junit.Test
import org.junit.Assert.*

class CorpusTextTest : QBaseTest() {
    @Test
    fun openCompoundAssociationsAllowEachDerivationLinkType() {
        for (type in listOf(Link.Derived, Link.Variation, Link.Transcription)) {
            val head = q.word("lassi", "leaves")
            val form = q.word("lassi", "explicit word gloss")
            val rule = q.rule("- no change", name = "case-${type.id}", addedCategories = ".ACC")
            graph.addLink(form, head, type, listOf(rule))
            val compound = graph.createOpenCompound(listOf(head), 0, "foliage")
            val text = q.corpusText("lassi")

            text.associateWord(0, form, openCompound = compound)
            val corpusWord = text.mapToLines().single().corpusWords.single()
            assertSame(form, corpusWord.word)
            assertEquals("foliage.ACC", corpusWord.gloss)
            assertEquals("foliage.ACC", corpusWord.segmentedGloss)
            compound.gloss = null
            assertNull(text.mapToLines().single().corpusWords.single().gloss)
        }
    }

    @Test
    fun openCompoundGlossAppliesRulesInOrderAcrossMixedLinkChain() {
        val head = q.word("lasse", "leaf")
        val plural = q.word("lassi", "leaves")
        val variant = q.word("lassie", "variant")
        val transcription = q.word("lassië", "transcription")
        val pluralRule = q.rule("- no change", name = "plural", addedCategories = ".PL").also {
            it.replacedCategories = ".SG"
        }
        val accusative = q.rule("- no change", name = "accusative", addedCategories = ".ACC")
        val genitive = q.rule("- no change", name = "genitive", addedCategories = ".GEN").also {
            it.replacedCategories = ".ACC"
        }
        val emphasis = q.rule("- no change", name = "emphasis", addedCategories = ".EMPH")
        graph.addLink(plural, head, Link.Derived, listOf(pluralRule))
        graph.addLink(variant, plural, Link.Variation, listOf(accusative, genitive))
        graph.addLink(transcription, variant, Link.Transcription, listOf(emphasis))
        val compound = graph.createOpenCompound(listOf(head), 0, "foliage.SG")
        val text = q.corpusText("lassië")

        text.associateWord(0, transcription, openCompound = compound)
        val corpusWord = text.mapToLines().single().corpusWords.single()
        assertEquals("foliage.PL.GEN.EMPH", corpusWord.gloss)
        assertEquals(corpusWord.gloss, corpusWord.segmentedGloss)
    }

    @Test
    fun openCompoundGlossPreservesSegmentedCategoriesOnHeadAndModifier() {
        val head = q.word("lassi", "leaves")
        val modifier = q.word("laurie", "golden")
        val headForm = q.word("lassillo", "leaves.ABL")
        val modifierForm = q.word("lauriello", "golden.ABL")
        val ablative = q.rule("- append 'llo'", name = "ablative", addedCategories = ".ABL")
        graph.addLink(headForm, head, Link.Derived, listOf(ablative))
        graph.addLink(modifierForm, modifier, Link.Derived, listOf(ablative))
        val compound = graph.createOpenCompound(listOf(modifier, head), 1, "foliage")
        val text = q.corpusText("lauriello lassillo")
        text.associateWord(0, modifierForm, openCompound = compound)
        text.associateWord(1, headForm, openCompound = compound)

        val words = text.mapToLines().single().corpusWords
        assertEquals(listOf("...-ABL", "foliage-ABL"), words.map { it.gloss })
        assertEquals(listOf("...-ABL", "foliage-ABL"), words.map { it.segmentedGloss })
    }

    @Test
    fun openCompoundAssociationRejectsOtherLinkTypesAndReverseLinks() {
        for (type in listOf(Link.Origin, Link.Related)) {
            val head = q.word("lassi", "leaves")
            val intermediate = q.word("lasse", "intermediate")
            val form = q.word("lassie", "form")
            graph.addLink(form, intermediate, Link.Derived)
            graph.addLink(intermediate, head, type)
            val compound = graph.createOpenCompound(listOf(head), 0, "foliage")
            assertThrows(IllegalArgumentException::class.java) {
                q.corpusText("lassie").associateWord(0, form, openCompound = compound)
            }
        }
        val head = q.word("lassi", "leaves")
        val form = q.word("lasse", "form")
        graph.addLink(head, form, Link.Derived)
        val compound = graph.createOpenCompound(listOf(head), 0, "foliage")
        assertThrows(IllegalArgumentException::class.java) {
            q.corpusText("lasse").associateWord(0, form, openCompound = compound)
        }
    }

    @Test
    fun openCompoundAssociationSearchHandlesCyclesAndAlternativePathsById() {
        val head = q.word("lassi", "leaves")
        val first = q.word("lasse", "form")
        val second = q.word("lasse", "form")
        graph.addLink(first, second, Link.Derived)
        graph.addLink(second, first, Link.Variation)
        val compound = graph.createOpenCompound(listOf(head), 0, "foliage")
        val text = q.corpusText("lasse")
        assertThrows(IllegalArgumentException::class.java) {
            text.associateWord(0, first, openCompound = compound)
        }

        graph.addLink(second, head, Link.Transcription)
        text.associateWord(0, first, openCompound = compound)
        assertEquals("foliage", text.mapToLines().single().corpusWords.single().gloss)
    }

    @Test
    fun openCompoundGloss() {
        val modifier = q.word("laurie", "golden")
        val head = q.word("lassi", "leaves")
        val other = q.word("lantar", "fall")
        val compound = graph.createOpenCompound(listOf(modifier, head), 1, "golden foliage")
        val text = q.corpusText("laurie lassi lantar")
        text.associateWord(0, modifier, openCompound = compound)
        text.associateWord(1, head, "context gloss", compound)
        text.associateWord(2, other)

        val words = text.mapToLines().single().corpusWords
        assertEquals(listOf("...", "golden foliage", "fall"), words.map { it.gloss })
        assertEquals(listOf("...", "golden foliage", "fall"), words.map { it.segmentedGloss })
        assertEquals("context gloss", words[1].contextGloss)
        assertSame(compound, words[0].openCompound)
        assertSame(compound, words[1].openCompound)
        assertSame(compound, text.words[0].openCompound)
        assertNull(words[2].openCompound)
    }

    @Test
    fun openCompoundHeadIsMatchedById() {
        val head = graph.addWord("mir", q, "jewel")
        val modifier = graph.addWord("mir", q, "jewel")
        val compound = graph.createOpenCompound(listOf(head, modifier), 0, "jewels")
        val text = q.corpusText("mir mir")
        text.associateWord(0, head, openCompound = compound)
        text.associateWord(1, modifier, openCompound = compound)

        assertEquals(listOf("jewels", "..."), text.mapToLines().single().corpusWords.map { it.gloss })
        compound.gloss = null
        assertNull(text.mapToLines().single().corpusWords[0].gloss)
    }

    @Test
    fun reassociateWordClearsOpenCompound() {
        val head = q.word("lassi", "leaves")
        val modifier = q.word("laurie", "golden")
        val compound = graph.createOpenCompound(listOf(modifier, head), 1, "foliage")
        val text = q.corpusText("lassi")
        text.associateWord(0, head)
        text.associateWord(0, head, openCompound = compound)
        assertEquals("foliage", text.mapToLines().single().corpusWords.single().gloss)

        text.associateWord(0, head)
        assertNull(text.words.single().openCompound)
        assertEquals("leaves", text.mapToLines().single().corpusWords.single().gloss)
    }

    @Test
    fun editTextPreservesOpenCompoundAssociation() {
        val head = q.word("lassi", "leaves")
        val modifier = q.word("laurie", "golden")
        val compound = graph.createOpenCompound(listOf(modifier, head), 1, "foliage")
        val text = q.corpusText("ai laurie lassi")
        text.associateWord(1, modifier, openCompound = compound)
        text.associateWord(2, head, "context gloss", compound)

        text.text = "laurie lassi"
        assertEquals(listOf(0, 1), text.words.map { it.index })
        assertTrue(text.words.all { it.openCompound === compound })
        assertEquals(listOf("...", "foliage"), text.mapToLines().single().corpusWords.map { it.gloss })
        assertEquals("context gloss", text.words[1].contextGloss)
    }

    @Test
    fun associateWordRejectsNonMemberWithoutChangingAssociation() {
        val head = q.word("lassi", "leaves")
        val other = q.word("laurie", "golden")
        val compound = graph.createOpenCompound(listOf(head), 0, "foliage")
        val text = q.corpusText("lassi")
        text.associateWord(0, head, "context gloss", compound)

        assertThrows(IllegalArgumentException::class.java) {
            text.associateWord(0, other, openCompound = compound)
        }
        assertSame(head, text.words.single().word)
        assertSame(compound, text.words.single().openCompound)
        assertEquals("context gloss", text.words.single().contextGloss)
    }

    @Test
    fun deletingOpenCompoundClearsCorpusReferences() {
        val head = q.word("lassi", "leaves")
        val modifier = q.word("laurie", "golden")
        val compound = graph.createOpenCompound(listOf(modifier, head), 1, "foliage")
        val text = graph.addCorpusText("laurie lassi", null, q)
        text.associateWord(0, modifier, openCompound = compound)
        text.associateWord(1, head, openCompound = compound)

        graph.deleteOpenCompound(compound)
        assertTrue(text.words.all { it.openCompound == null })
        assertEquals(listOf("golden", "leaves"), text.mapToLines().single().corpusWords.map { it.gloss })
    }

    @Test
    fun wordIndex() {
        val corpusText = q.corpusText("ai laurie lantar\nyeni unotime")
        val lines = corpusText.mapToLines()
        assertEquals(0, lines[0].corpusWords[0].index)
        assertEquals(3, lines[1].corpusWords[0].index)
    }

    @Test
    fun wordIndexTrailingSpace() {
        val corpusText = q.corpusText("ai laurie lantar \nyeni unotime")
        val lines = corpusText.mapToLines()
        assertEquals(0, lines[0].corpusWords[0].index)
        assertEquals(3, lines[1].corpusWords[0].index)
    }

    @Test
    fun associateWord() {
        val corpusText = q.corpusText("ai laurie lantar")
        val laurie = q.word("laurie")
        corpusText.associateWord(1, laurie)
        val line = corpusText.mapToLines().single()
        assertEquals(laurie, line.corpusWords[1].word)
    }

    @Test
    fun editText() {
        val corpusText = q.corpusText("ai laurie lantar")
        val laurie = q.word("laurie")
        corpusText.associateWord(1, laurie, "goldenly")

        corpusText.text = "laurie lantar"
        val line = corpusText.mapToLines().single()
        assertEquals(laurie, line.corpusWords[0].word)
        assertEquals("goldenly", line.corpusWords[0].contextGloss)
    }

    @Test
    fun normalizedText() {
        val corpusText = q.corpusText("ai lau[rie] lant⸢a⸣r")

        val lines = corpusText.mapToLines()
        assertEquals("laurie", lines[0].corpusWords[1].normalizedText)
        assertEquals("lantar", lines[0].corpusWords[2].normalizedText)
    }

    @Test
    fun decapitalizeAfterParagram() {
        val corpusText = q.corpusText("cave.\n\nNuper")

        val lines = corpusText.mapToLines()
        assertEquals("nuper", lines[2].corpusWords[0].normalizedText)
    }

    @Test
    fun normalizedTextDecapitalized() {
        val corpusText = q.corpusText("Ai Laurie. Lantar? Lassi")

        val lines = corpusText.mapToLines()
        assertEquals("ai", lines[0].corpusWords[0].normalizedText)
        assertEquals("Laurie", lines[0].corpusWords[1].normalizedText)
        assertEquals("lantar", lines[0].corpusWords[2].normalizedText)
        assertEquals("lassi", lines[0].corpusWords[3].normalizedText)
    }

    @Test
    fun normalizedTextRemoveQuotes() {
        val corpusText = q.corpusText("quet \"Ai laurie lantar,\"")

        val lines = corpusText.mapToLines()
        assertEquals("ai", lines[0].corpusWords[1].normalizedText)
        assertEquals("lantar", lines[0].corpusWords[3].normalizedText)
    }

    @Test
    fun normalizedTextRemoveApostrophes() {
        val corpusText = q.corpusText("quet 'Ai laurie lantar,'")

        val lines = corpusText.mapToLines()
        assertEquals("ai", lines[0].corpusWords[1].normalizedText)
        assertEquals("lantar", lines[0].corpusWords[3].normalizedText)
    }

    @Test
    fun normalizedTextRemoveQuotesAssociate() {
        val corpusText = q.corpusText("quet \"Ai laurie lantar,\"")

        val ai = q.word("ai", "ai")
        corpusText.associateWord(1, ai)
        val lines = corpusText.mapToLines()
        assertEquals("ai", lines[0].corpusWords[1].normalizedText)
        assertEquals("\"Ai", lines[0].corpusWords[1].segmentedText)
        assertEquals("lantar", lines[0].corpusWords[3].normalizedText)
    }

    @Test
    fun normalizedTextMaintainOrthography() {
        val corpusText = q.corpusText("ai laurië lantar")
        val lines = corpusText.mapToLines()
        assertEquals("laurië", lines[0].corpusWords[1].normalizedText)
    }

    @Test
    fun normalizedTextRemoveParentheses() {
        val corpusText = q.corpusText("Perhael (i sennui Panthael)")

        val stressRule = q.rule("- stress is on first syllable")
        q.stressRule = RuleRef.to(stressRule)

        val i = q.word("i", "i")
        corpusText.associateWord(1, i)

        val lines = corpusText.mapToLines()
        assertEquals("i", lines[0].corpusWords[1].normalizedText)
        assertEquals("(i", lines[0].corpusWords[1].segmentedText)
        assertEquals(1, lines[0].corpusWords[1].stressIndex)
        assertEquals("Panthael", lines[0].corpusWords[3].normalizedText)
    }

    @Test
    fun normalizedTextRemoveWideAngleBrackets() {
        val corpusText = q.corpusText("〈Ai〉 laurie")

        val lines = corpusText.mapToLines()
        assertEquals("ai", lines[0].corpusWords[0].normalizedText)
    }

    @Test
    fun attestations() {
        val eaV = q.word("ea", "be")
        val eaN = q.word("ea", "being")
        val ct1 = graph.addCorpusText("ea", null, q)
        ct1.associateWord(0, eaV)
        val ct2 = graph.addCorpusText("ea", null, q)
        ct2.associateWord(0, eaN)

        val attestations = graph.findAttestations(eaV)
        assertEquals(1, attestations.size)
    }

    @Test
    fun attestationsNotAssigned() {
        val eaV = q.word("lantar", "fall")
        graph.addCorpusText("ai laurie lantar", null, q)

        val attestations = graph.findAttestations(eaV)
        assertEquals(1, attestations.size)
    }

    @Test
    fun associateContextGloss() {
        val ct1 = graph.addCorpusText("ai laurie lantar", null, q)
        val laurie = q.word("laurie", "golden")
        ct1.associateWord(1, laurie, "goldenly")
        val lines = ct1.mapToLines()
        assertEquals("goldenly", lines[0].corpusWords[1].contextGloss)
    }

    @Test
    fun lockWordAssociations() {
        val ct1 = graph.addCorpusText("ai laurie lantar", null, q)
        val laurie = q.word("laurie", "golden")
        ct1.lockWordAssociations()
        assertEquals(laurie, ct1.words.single().word)
    }

    @Test
    fun syllabographicCandidate() {
        val hittite = graph.addLanguage("Hittite", "Hitt").also { it.syllabographic = true }
        val nu = hittite.word("nu", "and", syllabographic = true)
        val text = hittite.corpusText("nu")
        val lines = text.mapToLines()
        val word = lines[0].corpusWords[0]
        assertEquals(nu, word.wordCandidates!!.single())
    }

    @Test
    fun punctuationUnglossable() {
        val text = q.corpusText("[ ai ||")
        val lines = text.mapToLines()
        assertEquals(3, lines[0].corpusWords.size)
        assertFalse(lines[0].corpusWords[0].glossable)
        assertTrue(lines[0].corpusWords[1].glossable)
        assertFalse(lines[0].corpusWords[2].glossable)
    }

    @Test
    fun numbersUnglossable() {
        val text = q.corpusText("ai 30")
        val lines = text.mapToLines()
        assertEquals(2, lines[0].corpusWords.size)
        assertTrue(lines[0].corpusWords[0].glossable)
        assertFalse(lines[0].corpusWords[1].glossable)
    }

    @Test
    fun separator() {
        val text = q.corpusText("---")
        val lines = text.mapToLines()
        assertTrue(lines[0].separator)
        assertEquals(0, text.wordCount())
    }

    @Test
    fun lineNumber() {
        val text = q.corpusText("1# ai laurie")
        val lines = text.mapToLines()
        assertEquals("1", lines[0].lineNumber)
        assertEquals(2, text.wordCount())
    }
}

fun Language.corpusText(text: String) = CorpusText(-1, text, null, this)
