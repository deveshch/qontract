package `in`.specmatic.core.utilities

import `in`.specmatic.core.pattern.isPatternToken
import `in`.specmatic.core.pattern.withoutPatternDelimiters
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.stream.Collectors

object URIUtils {
    fun parseQuery(query: String?): Map<String, String> {
        if (query == null) {
            return emptyMap()
        }
        val pairs = query.split("&".toRegex()).toTypedArray()
        val queryParams = HashMap<String, String>()
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            if(idx < 0) {
                throw Exception("a part of the query string does not seem to be a key-value pair: $pair")
            }
            queryParams[URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8.toString())] = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8.toString())
        }
        return queryParams
    }

    fun parsePathParams(rawPath: String): Map<String, String> {
        val pathParts = rawPath.split("/".toRegex()).toTypedArray()
        val pathParams = HashMap<String, String>()
        for (pathPart in java.util.Arrays.stream(pathParts).filter { isPatternToken(it) }.collect(Collectors.toList())) {
            val nameAndType = getNameAndType(pathPart)
            pathParams[nameAndType[0]] = "(" + nameAndType[1] + ")"
        }
        return pathParams
    }

    private fun getNameAndType(placeHolder: String): Array<String> {
        return withoutPatternDelimiters(placeHolder).split(":".toRegex()).toTypedArray()
    }
}