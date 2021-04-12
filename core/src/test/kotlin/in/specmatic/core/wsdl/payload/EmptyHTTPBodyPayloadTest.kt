package `in`.specmatic.core.wsdl.payload

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class EmptyHTTPBodyPayloadTest {
    @Test
    fun `generates no statements`() {
        val statements = EmptyHTTPBodyPayload().qontractStatement()
        assertThat(statements).isEmpty()
    }
}
