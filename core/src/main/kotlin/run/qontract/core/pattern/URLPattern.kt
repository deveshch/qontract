package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.value.StringValue
import run.qontract.core.value.Value
import java.net.URI

data class URLPattern(val scheme: URLScheme = URLScheme.HTTPS): Pattern {
    override val pattern: String = "(url)"

    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        return when (sampleData) {
            is StringValue -> {
                if(parse(sampleData.string, resolver).string.startsWith(scheme.prefix)) {
                    Result.Success()
                } else Result.Failure("Expected URL prefix ${scheme.prefix} in url ${sampleData.string}")
            }
            else -> Result.Failure("URLs can only be held in strings.")
        }
    }

    override fun generate(resolver: Resolver): StringValue =
            StringValue("${scheme.prefix}${randomString().toLowerCase()}.com/${randomString().toLowerCase()}")

    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> = listOf(this)

    override fun parse(value: String, resolver: Resolver): StringValue = StringValue(URI.create(value).toString())
    override fun matchesPattern(pattern: Pattern, resolver: Resolver): Boolean {
        return this == pattern
    }

    override val description: String = "url"
}