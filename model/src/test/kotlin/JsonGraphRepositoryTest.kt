package ru.yole.etymograph

import org.junit.Assert.assertEquals
import org.junit.Test

class JsonGraphRepositoryTest {
    val q = Language("Quenya", "Q")

    @Test
    fun deletedWords() {
        val repo = JsonGraphRepository(null)
        repo.addLanguage(q)

        val abc = repo.addWord("abc")
        val def = repo.addWord("def")
        repo.deleteWord(abc)

        val json = repo.toJson()
        val repo2 = JsonGraphRepository.fromJsonString(json)
        assertEquals(null, repo2.wordById(abc.id))
        assertEquals("def", repo2.wordById(def.id)!!.text)
    }

    fun GraphRepository.addWord(text: String) = findOrAddWord(text, q, text)
}
