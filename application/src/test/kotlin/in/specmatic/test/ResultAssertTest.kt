package `in`.specmatic.test

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import `in`.specmatic.core.parseGherkinStringToFeature
import `in`.specmatic.test.ResultAssert.Companion.assertThat

internal class ResultAssertTest {
    @Test
    fun `it should not allow a failing test on a finalised scenario through`() {
        val feature = parseGherkinStringToFeature("""
            Feature: Test feature
            
            Scenario: Test scenario
              When POST /
              Then status 200
        """.trimIndent())

        val failure = `in`.specmatic.core.Result.Failure("For some reason").updateScenario(feature.scenarios.single())
        assertThatThrownBy { assertThat(failure).isSuccess() }.isInstanceOf(java.lang.AssertionError::class.java)
    }
}