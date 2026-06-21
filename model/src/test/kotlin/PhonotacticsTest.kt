package page.yole.etymograph

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhonotacticsTest : QBaseTest() {
    @Test
    fun simple() {
        val phono = parseRule(q, q, "word ends with not vowel and word ends with not 'n':\n- disallow")
        q.phonotacticsRule = RuleRef.to(phono)
        assertTrue(graph.matchesPhonotactics(q, "elen"))
        assertTrue(graph.matchesPhonotactics(q, "sila"))
        assertFalse(graph.matchesPhonotactics(q, "balrog"))
    }
}
