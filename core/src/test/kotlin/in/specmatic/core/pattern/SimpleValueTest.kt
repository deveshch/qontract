package `in`.specmatic.core.pattern

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class SimpleValueTest {
    @Test
    fun `returns the value it was given`() {
        val rowValue = SimpleValue("data")
        assertThat(rowValue.fetch()).isEqualTo("data")
    }
}