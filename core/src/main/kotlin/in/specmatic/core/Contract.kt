package `in`.specmatic.core

import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.stub.HttpStub
import `in`.specmatic.test.HttpClient

data class Contract(val contractGherkin: String) {
    fun test(endPoint: String) {
        val contractBehaviour = parseGherkinStringToFeature(contractGherkin)
        val results = contractBehaviour.executeTests(HttpClient(endPoint))
        if (results.hasFailures())
            throw ContractException(results.report())
    }

    fun test(fake: HttpStub) = test(fake.endPoint)

    fun samples(fake: HttpStub) = samples(fake.endPoint)
    fun samples(endPoint: String) {
        val contractBehaviour = parseGherkinStringToFeature(contractGherkin)
        val httpClient = HttpClient(endPoint)

        contractBehaviour.generateContractTestScenarios(emptyList()).fold(Results()) { results, scenario ->
            when(val kafkaMessagePattern = scenario.kafkaMessagePattern) {
                null -> Results(results = results.results.plus(executeTest(scenario, httpClient)).toMutableList())
                else -> {
                    val message = """KAFKA MESSAGE
${kafkaMessagePattern.generate(scenario.resolver).toDisplayableString()}""".trimMargin().prependIndent("| ")
                    println(message)
                    Results(results = results.results.plus(Result.Success()).toMutableList())
                }
            }
        }
    }
}

fun fromGherkin(contractGherkin: String): Contract {
    return Contract(contractGherkin)
}
