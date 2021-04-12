package `in`.specmatic.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class TestBackwardCompatibilityKtTest {
    @Test
    fun `contract backward compatibility should break when one has an optional key and the other does not` () {
        val gherkin1 = """
Feature: Older contract API

Scenario: api call
Given json Value
| value     | (number) |
| optional? | (number) |
When POST /value
And request-body (Value)
Then status 200
    """.trim()

        val gherkin2 = """
Feature: Older contract API

Scenario: api call
Given json Value
| value    | (number) |
| optional | (number) |
When POST /value
And request-body (Value)
Then status 200
    """.trim()

        val olderContract = parseGherkinStringToFeature(gherkin1)
        val newerContract = parseGherkinStringToFeature(gherkin2)

        val result: Results = testBackwardCompatibilityInParallel(olderContract, newerContract)

        println(result.report())

        assertEquals(1, result.failureCount)
        assertEquals(1, result.successCount)
    }

    @Test
    fun `contract backward compatibility should not break when both have an optional keys` () {
        val gherkin1 = """
Feature: API contract

Scenario: api call
Given json Value
| value     | (number) |
| optional? | (number) |
And fact id 10
When POST /value/(id:number)
And request-body (Value)
Then status 200
    """.trim()

        val gherkin2 = """
Feature: API contract

Scenario: api call
Given json Value
| value    | (number) |
| optional? | (number) |
And fact id 10
When POST /value/(id:number)
And request-body (Value)
Then status 200
    """.trim()

        val olderContract = parseGherkinStringToFeature(gherkin1)
        val newerContract = parseGherkinStringToFeature(gherkin2)

        val result: Results = testBackwardCompatibilityInParallel(olderContract, newerContract)

        println(result.report())

        assertEquals(2, result.successCount)
        assertEquals(0, result.failureCount)
    }

    @Test
    fun `contract backward compatibility should break when a new fact is added` () {
        val gherkin1 = """
Feature: Older contract API

Scenario: api call
Given json Value
| value     | (number) |
| optional? | (number) |
When POST /value/(id:number)
And request-body (Value)
Then status 200
    """.trim()

        val gherkin2 = """
Feature: Older contract API

Scenario: api call
Given json Value
| value    | (number) |
| optional? | (number) |
And fact id 10
When POST /value/(id:number)
And request-body (Value)
Then status 200
    """.trim()

        val olderContract = parseGherkinStringToFeature(gherkin1)
        val newerContract = parseGherkinStringToFeature(gherkin2)

        val results: Results = testBackwardCompatibilityInParallel(olderContract, newerContract)

        assertEquals(0, results.successCount)
        assertEquals(2, results.failureCount)
    }

    @Test
    fun `contract should test successfully against itself when fact name is specified without a value in the URL path`() {
        val gherkin = """
Feature: Contract API

Scenario: api call
Given fact id
When POST /value/(id:number)
Then status 200
    """.trim()

        val contract = parseGherkinStringToFeature(gherkin)

        val results: Results = testBackwardCompatibilityInParallel(contract, contract)

        if(results.failureCount > 0)
            println(results.report())

        assertEquals(1, results.successCount)
        assertEquals(0, results.failureCount)
    }

    @Test
    fun `contract should test successfully against itself when fact name is specified without a value in the query`() {
        val gherkin = """
Feature: Contract API

Scenario: Test Contract
Given fact id
When GET /value?id=(number)
Then status 200
    """.trim()

        val contract = parseGherkinStringToFeature(gherkin)

        val results: Results = testBackwardCompatibilityInParallel(contract, contract)

        if(results.failureCount > 0)
            println(results.report())

        assertEquals(2, results.successCount)
        assertEquals(0, results.failureCount)
    }

    @Test
    fun `should be able to validate new contract compatibility with optional request body`() {
        val gherkin = """
Feature: Contract API

Scenario: api call
When POST /number
And request-body (number?)
Then status 200
    """.trim()

        val contract = parseGherkinStringToFeature(gherkin)

        val results: Results = testBackwardCompatibilityInParallel(contract, contract)

        if(results.failureCount > 0)
            println(results.report())

        assertEquals(2, results.successCount)
        assertEquals(0, results.failureCount)
    }

    @Test
    fun `should be able to validate new contract compatibility with optional key in request body`() {
        val gherkin = """
Feature: Contract API

Scenario: api call
Given json Number
| number | (number?) |
When POST /number
And request-body (Number)
Then status 200
    """.trim()

        val contract = parseGherkinStringToFeature(gherkin)

        val results: Results = testBackwardCompatibilityInParallel(contract, contract)

        if(results.failureCount > 0)
            println(results.report())

        assertEquals(2, results.successCount)
        assertEquals(0, results.failureCount)
    }

    @Test
    fun `should be able to validate new contract compatibility with optional response body`() {
        val gherkin = """
Feature: Contract API

Scenario: api call
When POST /number
Then status 200
And response-body (number?)
    """.trim()

        val contract = parseGherkinStringToFeature(gherkin)

        val results: Results = testBackwardCompatibilityInParallel(contract, contract)

        if(results.failureCount > 0)
            println(results.report())

        assertEquals(1, results.successCount)
        assertEquals(0, results.failureCount)
    }

    @Test
    fun `should be able to validate new contract compatibility with optional key in response body`() {
        val gherkin = """
Feature: Contract API

Scenario: api call
Given json Number
| number | (number?) |
When POST /number
Then status 200
And response-body (Number)
    """.trim()

        val contract = parseGherkinStringToFeature(gherkin)

        val results: Results = testBackwardCompatibilityInParallel(contract, contract)

        if(results.failureCount > 0)
            println(results.report())

        assertEquals(1, results.successCount)
        assertEquals(0, results.failureCount)
    }

    @Test
    fun `contract with a required key should not match a contract with the same key made optional`() {
        val olderBehaviour = parseGherkinStringToFeature("""
Feature: Contract API

Scenario: api call
When POST /number
And request-body (number)
Then status 200
And response-body
| number      | (number) |
| description | (string) |
""".trim())

        val newerBehaviour = parseGherkinStringToFeature("""
Feature: Contract API

Scenario: api call
When POST /number
And request-body (number)
Then status 200
And response-body
| number       | (number) |
| description? | (string) |
""".trim())

        val results: Results = testBackwardCompatibilityInParallel(olderBehaviour, newerBehaviour)

        println(results.report())

        assertEquals(0, results.successCount)
        assertEquals(1, results.failureCount)
    }

    @Test
    fun `contract with an optional key in the response should pass against itself`() {
        val behaviour = parseGherkinStringToFeature("""
Feature: Contract API

Scenario: api call
When POST /number
And request-body (number)
Then status 200
And response-body
| number       | (number) |
| description? | (string) |
""".trim())

        val results: Results = testBackwardCompatibilityInParallel(behaviour, behaviour)

        println(results.report())
        assertEquals(1, results.successCount)
        assertEquals(0, results.failureCount)
    }

    @Test
    fun `should work with multipart content part`() {
        val behaviour = parseGherkinStringToFeature("""
Feature: Contract API

Scenario: api call
When POST /number
And request-part number (number)
Then status 200
And response-body
| number       | (number) |
| description? | (string) |
""".trim())

        val results: Results = testBackwardCompatibilityInParallel(behaviour, behaviour)

        println(results.report())
        assertEquals(1, results.successCount)
        assertEquals(0, results.failureCount)
    }

    @Test
    fun `should work with multipart file part`() {
        val behaviour = parseGherkinStringToFeature("""
Feature: Contract API

Scenario: api call
When POST /number
And request-part number @number.txt text/plain
Then status 200
And response-body
| number       | (number) |
| description? | (string) |
""".trim())

        val results: Results = testBackwardCompatibilityInParallel(behaviour, behaviour)

        println(results.report())
        assertEquals(1, results.successCount)
        assertEquals(0, results.failureCount)
    }

    @Test
    fun `should fail given a file part in one and a content part in the other`() {
        val older = parseGherkinStringToFeature("""
Feature: Contract API

Scenario: api call
When POST /number
And request-part number @number.txt text/plain
Then status 200
And response-body
| number       | (number) |
| description? | (string) |
""".trim())

        val newer = parseGherkinStringToFeature("""
Feature: Contract API

Scenario: api call
When POST /number
And request-part number (number))
Then status 200
And response-body
| number       | (number) |
| description? | (string) |
""".trim())

        val results: Results = testBackwardCompatibilityInParallel(older, newer)

        println(results.report())
        assertThat(results.success()).isFalse()
    }

    @Test
    fun `a contract should be backward compatible with itself`() {
        val gherkin = """
Feature: Contract API

Scenario: api call
When POST /number
Then status 200
And response-body (number)
    """.trim()

        val contract = parseGherkinStringToFeature(gherkin)

        val results: Results = testBackwardCompatibilityInParallel(contract, contract)

        if(results.failureCount > 0)
            println(results.report())

        assertEquals(1, results.successCount)
        assertEquals(0, results.failureCount)
    }

    @Test
    fun `a contract with named patterns should be backward compatible with itself`() {
        val gherkin = """
Feature: Contract API

Scenario: api call
Given json Payload
  | number | (number) |
When POST /number
  And request-body (Payload)
Then status 200
And response-body (number)
    """.trim()

        val contract = parseGherkinStringToFeature(gherkin)

        val results: Results = testBackwardCompatibilityInParallel(contract, contract)

        if(results.failureCount > 0)
            println(results.report())

        assertEquals(1, results.successCount)
        assertEquals(0, results.failureCount)
    }

    @Test
    fun `a contract with named patterns should not be backward compatible with another contract with a different pattern against the same name`() {
        val gherkin1 = """
Feature: Contract API

Scenario: api call
Given json Payload
  | number | (number) |
When POST /number
  And request-body (Payload)
Then status 200
And response-body (number)
    """.trim()

        val gherkin2 = """
Feature: Contract API

Scenario: api call
Given json Payload
  | number | (string) |
When POST /number
  And request-body (Payload)
Then status 200
And response-body (number)
    """.trim()

        val results: Results = testBackwardCompatibilityInParallel(parseGherkinStringToFeature(gherkin1), parseGherkinStringToFeature(gherkin2))

        if(results.failureCount > 0)
            println(results.report())

        assertEquals(0, results.successCount)
        assertEquals(1, results.failureCount)
    }

    @Test
    fun `a breaking WIP scenario should not break backward compatibility tests`() {
        val gherkin1 = """
Feature: Contract API

@WIP
Scenario: api call
When POST /data
  And request-body (number)
Then status 200
    """.trim()

        val gherkin2 = """
Feature: Contract API

@WIP
Scenario: api call
When POST /data
  And request-body (string)
Then status 200
    """.trim()

        val results: Results = testBackwardCompatibilityInParallel(parseGherkinStringToFeature(gherkin1), parseGherkinStringToFeature(gherkin2))

        if(results.failureCount > 0)
            println(results.report())

        assertThat(results.successCount).isZero()
        assertThat(results.failureCount).isZero()
        assertThat(results.success()).isTrue()
    }

    @Test
    fun `two xml contracts should be backward compatibility when the only thing changing is namespace prefixes`() {
        val gherkin1 = """
Feature: Contract API

Scenario: api call
When POST /data
  And request-body <ns1:customer xmlns:ns1="http://example.com/customer"><name>(string)</name></ns1:customer>
Then status 200
    """.trim()

        val gherkin2 = """
Feature: Contract API

Scenario: api call
When POST /data
  And request-body <ns2:customer xmlns:ns2="http://example.com/customer"><name>(string)</name></ns2:customer>
Then status 200
    """.trim()

        val results: Results = testBackwardCompatibilityInParallel(parseGherkinStringToFeature(gherkin1), parseGherkinStringToFeature(gherkin2))

        if(results.failureCount > 0)
            println(results.report())

        assertThat(results.successCount).isOne()
        assertThat(results.failureCount).isZero()
        assertThat(results.success()).isTrue()
    }
}
