package run.qontract.core

import org.junit.jupiter.api.Test
import run.qontract.core.pattern.Row
import run.qontract.core.pattern.keySets
import kotlin.test.assertEquals

class KeySetsTest {
    @Test
    fun `Given a single optional key, two sets of keys should be generated, one with and one without the key` () {
        val listOfKeys = mapOf("key1" to 1, "key2?" to 2).entries.toList()
        val expectedKeySets = listOf(listOf("key1"), listOf("key1", "key2?"))

        assertEquals(expectedKeySets, keySets(listOfKeys, Row(), Resolver()))
    }
}