package `in`.specmatic.mock

import `in`.specmatic.core.*
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.value.*

data class ScenarioStub(val request: HttpRequest = HttpRequest(), val response: HttpResponse = HttpResponse(0, emptyMap()), val kafkaMessage: KafkaMessage? = null, val delayInSeconds: Int? = null) {
    fun toJSON(): JSONObjectValue {
        val mockInteraction = mutableMapOf<String, Value>()
        if(kafkaMessage != null) {
            TODO("Implement serialisation")
        } else {
            mockInteraction[MOCK_HTTP_REQUEST] = request.toJSON()
            mockInteraction[MOCK_HTTP_RESPONSE] = response.toJSON()
        }

        return JSONObjectValue(mockInteraction)
    }
}

const val MOCK_KAFKA_MESSAGE = "kafka-message"
const val MOCK_HTTP_REQUEST = "http-request"
const val MOCK_HTTP_RESPONSE = "http-response"
const val DELAY_IN_SECONDS = "delay-in-seconds"

val MOCK_HTTP_REQUEST_ALL_KEYS = listOf("mock-http-request", MOCK_HTTP_REQUEST)
val MOCK_HTTP_RESPONSE_ALL_KEYS = listOf("mock-http-response", MOCK_HTTP_RESPONSE)

fun validateMock(mockSpec: Map<String, Any?>) {
    if (!mockSpec.containsKey(MOCK_KAFKA_MESSAGE)) {
        if (MOCK_HTTP_REQUEST_ALL_KEYS.none { mockSpec.containsKey(it) }) throw ContractException(errorMessage = "This spec does not contain information about either the kafka message or the request to be mocked.")
        if (MOCK_HTTP_RESPONSE_ALL_KEYS.none { mockSpec.containsKey(it) }) throw ContractException(errorMessage = "This spec does not contain information about the response to be mocked.")
    }
}

fun mockFromJSON(mockSpec: Map<String, Value>): ScenarioStub {
    return when {
        mockSpec.contains(MOCK_KAFKA_MESSAGE) -> ScenarioStub(kafkaMessage = kafkaMessageFromJSON(getJSONObjectValue(MOCK_KAFKA_MESSAGE, mockSpec)))
        else -> {
            val mockRequest = requestFromJSON(getJSONObjectValue(MOCK_HTTP_REQUEST_ALL_KEYS, mockSpec))
            val mockResponse = HttpResponse.fromJSON(getJSONObjectValue(MOCK_HTTP_RESPONSE_ALL_KEYS, mockSpec))

            val delayInSeconds = getIntOrNull(DELAY_IN_SECONDS, mockSpec)

            ScenarioStub(request = mockRequest, response = mockResponse, delayInSeconds = delayInSeconds)
        }
    }
}

private const val KAFKA_TOPIC_KEY = "topic"
private const val KAFKA_VALUE_KEY = "value"
private const val KAFKA_KEY_KEY = "key"

fun kafkaMessageFromJSON(json: Map<String, Value>): KafkaMessage {
    if(KAFKA_TOPIC_KEY !in json)
        throw ContractException("Kafka message stub info must contain a topic name")

    if(KAFKA_VALUE_KEY !in json)
        throw ContractException("Kafka message stub info must contain a payload")

    val target = json.getValue(KAFKA_TOPIC_KEY)
    val key = json[KAFKA_KEY_KEY]
    val value = json.getValue(KAFKA_VALUE_KEY)

    return KafkaMessage(target.toStringValue(), key?.let { StringValue(it.toStringValue()) }, value)
}

fun getJSONObjectValue(keys: List<String>, mapData: Map<String, Value>): Map<String, Value> {
    val key = keys.first { mapData.containsKey(it) }
    return getJSONObjectValue(key, mapData)
}

fun getJSONObjectValue(key: String, mapData: Map<String, Value>): Map<String, Value> {
    val data = mapData.getValue(key)
    if(data !is JSONObjectValue) throw ContractException("$key should be a json object")
    return data.jsonObject
}

fun getIntOrNull(key: String, mapData: Map<String, Value>): Int? {
    val data = mapData[key]

    return data?.let {
        if(data !is NumberValue) throw ContractException("$key should be a number")
        return data.number.toInt()
    }
}
