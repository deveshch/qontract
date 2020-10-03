package run.qontract.core.pattern

import io.cucumber.messages.Messages
import run.qontract.core.*
import run.qontract.core.utilities.mapZip
import run.qontract.core.utilities.stringToPatternMap
import run.qontract.core.utilities.withNullPattern
import run.qontract.core.value.*

fun toTabularPattern(jsonContent: String, typeAlias: String? = null): TabularPattern = toTabularPattern(stringToPatternMap(jsonContent), typeAlias)

fun toTabularPattern(map: Map<String, Pattern>, typeAlias: String? = null): TabularPattern {
    val missingKeyStrategy = when ("...") {
        in map -> ignoreUnexpectedKeys
        else -> ::validateUnexpectedKeys
    }

    return TabularPattern(map.minus("..."), missingKeyStrategy, typeAlias)
}

data class TabularPattern(override val pattern: Map<String, Pattern>, private val unexpectedKeyCheck: UnexpectedKeyCheck = ::validateUnexpectedKeys, override val typeAlias: String? = null) : Pattern {
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if(sampleData !is JSONObjectValue)
            return mismatchResult("JSON object", sampleData)

        val resolverWithNullType = withNullPattern(resolver)
        val missingKey = resolverWithNullType.findMissingKey(pattern, sampleData.jsonObject, unexpectedKeyCheck)
        if(missingKey != null)
            return missingKeyToResult(missingKey, "key")

        mapZip(pattern, sampleData.jsonObject).forEach { (key, patternValue, sampleValue) ->
            when (val result = resolverWithNullType.matchesPattern(key, patternValue, sampleValue)) {
                is Result.Failure -> return result.breadCrumb(key)
            }
        }

        return Result.Success()
    }

    override fun isScalar(resolver: Resolver): Boolean = false

    override fun listOf(valueList: List<Value>, resolver: Resolver): Value = JSONArrayValue(valueList)

    override fun generate(resolver: Resolver): JSONObjectValue {
        val resolverWithNullType = withNullPattern(resolver)
        return JSONObjectValue(pattern.mapKeys { entry -> withoutOptionality(entry.key) }.mapValues { (key, pattern) ->
            attempt(breadCrumb = key) { resolverWithNullType.generate(key, pattern) }
        })
    }

    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> {
        val resolverWithNullType = withNullPattern(resolver)
        return forEachKeyCombinationIn(pattern, row, resolver) { pattern ->
            newBasedOn(pattern, row, resolverWithNullType)
        }.map { toTabularPattern(it) }
    }

    override fun parse(value: String, resolver: Resolver): Value = parsedJSON(value)
    override fun encompasses(otherPattern: Pattern, thisResolver: Resolver, otherResolver: Resolver, typeStack: TypeStack): Result {
        val thisResolverWithNullType = withNullPattern(thisResolver)
        val otherResolverWithNullType = withNullPattern(otherResolver)

        return when (otherPattern) {
            is ExactValuePattern -> otherPattern.fitsWithin(listOf(this), otherResolverWithNullType, thisResolverWithNullType, typeStack)
            !is TabularPattern -> Result.Failure("Expected tabular json type, got ${otherPattern.typeName}")
            else -> mapEncompassesMap(pattern, otherPattern.pattern, thisResolverWithNullType, otherResolverWithNullType, typeStack)
        }
    }

    override val typeName: String = "json object"
}

fun newBasedOn(patternMap: Map<String, Pattern>, row: Row, resolver: Resolver): List<Map<String, Pattern>> {
    val patternCollection = patternMap.mapValues { (key, pattern) ->
        attempt(breadCrumb = key) {
            newBasedOn(row, key, pattern, resolver)
        }
    }

    return patternList(patternCollection)
}

fun newBasedOn(row: Row, key: String, pattern: Pattern, resolver: Resolver): List<Pattern> {
    val keyWithoutOptionality = key(pattern, key)

    return when {
        row.containsField(keyWithoutOptionality) -> {
            val rowValue = row.getField(keyWithoutOptionality)
            if (isPatternToken(rowValue)) {
                val rowPattern = resolver.getPattern(rowValue)

                attempt(breadCrumb = key) {
                    when (val result = pattern.encompasses(rowPattern, resolver, resolver)) {
                        is Result.Success -> rowPattern.newBasedOn(row, resolver)
                        else -> throw ContractException(resultReport(result))
                    }
                }
            } else {
                val parsedRowValue = attempt("Format error in example of \"$keyWithoutOptionality\"") {
                    pattern.parse(rowValue, resolver)
                }

                when(val matchResult = pattern.matches(parsedRowValue, resolver)) {
                    is Result.Failure -> throw ContractException(resultReport(matchResult))
                    else -> listOf(ExactValuePattern(parsedRowValue))
                }
            }
        }
        else -> pattern.newBasedOn(row, resolver)
    }
}

fun key(pattern: Pattern, key: String): String {
    return withoutOptionality(when (pattern) {
        is Keyed -> pattern.key ?: key
        else -> key
    })
}

fun <ValueType> patternList(patternCollection: Map<String, List<ValueType>>): List<Map<String, ValueType>> {
    if(patternCollection.isEmpty())
        return listOf(emptyMap())

    val key = patternCollection.keys.first()

    return (patternCollection[key] ?: throw ContractException("key $key should not be empty in $patternCollection"))
            .flatMap { pattern ->
                val subLists = patternList(patternCollection - key)
                subLists.map { generatedPatternMap ->
                    generatedPatternMap.plus(Pair(key, pattern))
                }
            }
}

fun <ValueType> forEachKeyCombinationIn(patternMap: Map<String, ValueType>, row: Row, resolver: Resolver, creator: (Map<String, ValueType>) -> List<Map<String, ValueType>>): List<Map<String, ValueType>> =
    keySets(patternMap.entries.toList(), row, resolver).map { keySet ->
        patternMap.filterKeys { key -> key in keySet }
    }.map { newPattern ->
        creator(newPattern)
    }.flatten()

internal fun <ValueType> keySets(listOfKeys: List<Map.Entry<String, ValueType>>, row: Row, resolver: Resolver): List<List<String>> {
    if(listOfKeys.isEmpty())
        return listOf(listOfKeys.map { it.key })

    val last = listOfKeys.last()
    val key = last.key
    val value = last.value
    val subLists = keySets(listOfKeys.dropLast(1), row, resolver)

    return subLists.flatMap { subList ->
        when {
            row.containsField(withoutOptionality(key)) -> listOf(subList + key)
            isOptional(key) -> when {
                isScalar(value, resolver) -> listOf(subList)
                else -> listOf(subList, subList + key)
            }
            else -> listOf(subList + key)
        }
    }
}

fun <ValueType> isScalar(value: ValueType, resolver: Resolver): Boolean {
    if(value is Pattern)
        return value.isScalar(resolver)

    if(value is String && isPatternToken(value))
        return resolver.getPattern(value).isScalar(resolver)

    return false
}

fun rowsToTabularPattern(rows: List<Messages.GherkinDocument.Feature.TableRow>, typeAlias: String? = null) =
        toTabularPattern(rows.map { it.cellsList }.map { (key, value) ->
            key.value to toJSONPattern(value.value)
        }.toMap(), typeAlias)

fun toJSONPattern(value: String): Pattern {
    return value.trim().let {
        val asNumber: Number? = try { convertToNumber(value) } catch (e: Throwable) { null }

        when {
            asNumber != null -> ExactValuePattern(NumberValue(asNumber))
            it.startsWith("\"") && it.endsWith("\"") ->
                ExactValuePattern(StringValue(it.removeSurrounding("\"")))
            it == "null" -> ExactValuePattern(NullValue)
            it == "true" -> ExactValuePattern(BooleanValue(true))
            it == "false" -> ExactValuePattern(BooleanValue(false))
            else -> parsedPattern(value)
        }
    }
}

fun isNumber(value: StringValue): Boolean {
    return try {
        convertToNumber(value.string)
        true
    } catch(e: ContractException) {
        false
    }
}

fun convertToNumber(value: String): Number = value.trim().let {
    stringToInt(it) ?: stringToLong(it) ?: stringToFloat(it) ?: stringToDouble(it) ?: throw ContractException("""Expected number, actual was "$value"""")
}

internal fun stringToInt(value: String): Int? = try { value.toInt() } catch(e: Throwable) { null }
internal fun stringToLong(value: String): Long? = try { value.toLong() } catch(e: Throwable) { null }
internal fun stringToFloat(value: String): Float? = try { value.toFloat() } catch(e: Throwable) { null }
internal fun stringToDouble(value: String): Double? = try { value.toDouble() } catch(e: Throwable) { null }
