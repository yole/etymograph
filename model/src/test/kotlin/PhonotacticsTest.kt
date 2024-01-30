package ru.yole.etymograph

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhonotacticsTest : QBaseTest() {
    @Test
    fun simple() {
        val repo = repoWithQ()
        val phono = parseRule(q, q, "word ends with not vowel and word ends with not 'n':\n- disallow")
        q.phonotacticsRule = RuleRef.to(phono)
        assertTrue(repo.matchesPhonotactics(q, "elen"))
        assertTrue(repo.matchesPhonotactics(q, "sila"))
        assertFalse(repo.matchesPhonotactics(q, "balrog"))
    }
}
