package run.qontract.core

import run.qontract.core.pattern.ContractException
import run.qontract.core.utilities.exceptionCauseMessage
import run.qontract.stub.HttpStub
import run.qontract.test.HttpClient

data class Contract(val contractGherkin: String) {
    fun startFake(port: Int) = HttpStub(contractGherkin, emptyList(), "localhost", port)

    fun test(endPoint: String) {
        val contractBehaviour = Feature(contractGherkin)
        val results = contractBehaviour.executeTests(HttpClient(endPoint))
        if (results.hasFailures())
            throw ContractException(results.report())
    }

    fun test(fake: HttpStub) = test(fake.endPoint)

    fun samples(fake: HttpStub) = samples(fake.endPoint)
    fun samples(endPoint: String) {
        val contractBehaviour = Feature(contractGherkin)
        val httpClient = HttpClient(endPoint)

        contractBehaviour.generateTestScenarios(emptyList()).fold(Results()) { results, scenario ->
            when(val kafkaMessagePattern = scenario.kafkaMessagePattern) {
                null -> {
                    val request = scenario.generateHttpRequest()
                    Results(results = results.results.plus(try {
                        httpClient.setServerState(scenario.serverState)

                        val response = httpClient.execute(request)

                        when (response.headers.getOrDefault(QONTRACT_RESULT_HEADER, "success")) {
                            "failure" -> Result.Failure(response.body.toStringValue()).updateScenario(scenario)
                            else -> scenario.matches(response)
                        }
                    } catch (exception: Throwable) {
                        Result.Failure(exceptionCauseMessage(exception))
                                .also { failure -> failure.updateScenario(scenario) }
                    }).toMutableList())
                }
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
