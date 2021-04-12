package `in`.specmatic.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import `in`.specmatic.core.GherkinSection.Given
import `in`.specmatic.core.pattern.DeferredPattern
import `in`.specmatic.core.pattern.toTabularPattern
import `in`.specmatic.core.value.UseExampleDeclarations

internal class GherkinClauseKtTest {
    @Test
    fun `pipes in tabular pattern is escaped`() {
        val clause = toClause("Data", toTabularPattern(mapOf("key|" to DeferredPattern("(Type|)"))))
        println(clause.content)
        assertThat(clause).isEqualTo(GherkinClause("type Data\n  | key\\| | (Type\\|) |", Given))
    }

    @Test
    fun `pipes in deferred pattern is escaped`() {
        val clause = toClause("Data", toTabularPattern(mapOf("key|" to DeferredPattern("(Type|)"))))
        println(clause.content)
        assertThat(clause).isEqualTo(GherkinClause("type Data\n  | key\\| | (Type\\|) |", Given))
    }

    @Test
    fun `pipes in examples keys and values are escaped`() {
        val examples = UseExampleDeclarations(mapOf("key|" to "value|"))
        val examplesString = toExampleGherkinString(examples)
        assertThat(examplesString).isEqualTo("Examples:\n| key\\| |\n| value\\| |")
    }
}
