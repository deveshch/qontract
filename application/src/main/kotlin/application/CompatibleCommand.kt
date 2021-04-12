package application

import `in`.specmatic.core.*
import `in`.specmatic.core.git.GitCommand
import `in`.specmatic.core.git.NonZeroExitError
import `in`.specmatic.core.git.SystemGit
import `in`.specmatic.core.utilities.exceptionCauseMessage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import java.io.FileNotFoundException
import java.util.concurrent.Callable

@Configuration
open class SystemObjects {
    @Bean
    open fun getSystemGit(): GitCommand {
        return SystemGit()
    }
}

@Command(name = "git",
        mixinStandardHelpOptions = true,
        description = ["Checks backward compatibility of a contract in a git repository"])
class GitCompatibleCommand : Callable<Int> {
    @Autowired
    lateinit var gitCommand: GitCommand

    @Autowired
    lateinit var fileOperations: FileOperations

    @Command(name = "file", description = ["Compare file in working tree against HEAD"])
    fun file(@Parameters(paramLabel = "contractPath") contractPath: String, @CommandLine.Option(names = ["numberOfThreads"], defaultValue = "3", required = false) numberOfThreads: Int): Int {
        val output = checkCompatibility {
            backwardCompatibleFile(contractPath, fileOperations, gitCommand, numberOfThreads)
        }

        println(output.message)
        return output.exitCode
    }

    @Command(name = "commits", description = ["Compare file in newer commit against older commit"])
    fun commits(@Parameters(paramLabel = "contractPath") path: String, @Parameters(paramLabel = "newerCommit") newerCommit: String, @Parameters(paramLabel = "olderCommit") olderCommit: String, @CommandLine.Option(names = ["numberOfThreads"], defaultValue = "3", required = false) numberOfThreads: Int): Int {
        println("Using $numberOfThreads threads")

        val output = checkCompatibility {
            backwardCompatibleCommit(path, newerCommit, olderCommit, gitCommand, numberOfThreads)
        }

        println(output.message)
        return output.exitCode
    }

    override fun call(): Int {
        CommandLine(GitCompatibleCommand()).usage(System.out)
        return 0
    }
}

@Command(name = "compatible",
        mixinStandardHelpOptions = true,
        description = ["Checks if the newer contract is backward compatible with the older one"],
        subcommands = [ GitCompatibleCommand::class ])
internal class CompatibleCommand : Callable<Unit> {
    override fun call() {
        CommandLine(CompatibleCommand()).usage(System.out)
    }
}

internal fun compatibilityReport(results: Results, resultMessage: String): String {
    val countsMessage = "Tests run: ${results.successCount + results.failureCount}, Passed: ${results.successCount}, Failed: ${results.failureCount}\n\n"
    val resultReport = results.report().trim().let {
        when {
            it.isNotEmpty() -> "$it\n\n"
            else -> it
        }
    }

    return "$countsMessage$resultReport$resultMessage".trim()
}

internal fun backwardCompatibleFile(
    newerContractPath: String,
    fileOperations: FileOperations,
    git: GitCommand,
    numberOfThreads: Int? = null
): Outcome<Results> {
    return try {
        val newerFeature = parseGherkinStringToFeature(fileOperations.read(newerContractPath))
        val result = getOlderFeature(newerContractPath, git)

        result.onSuccess {
            Outcome(testBackwardCompatibility(it, newerFeature, numberOfThreads ?: 1))
        }
    } catch(e: NonZeroExitError) {
        Outcome(Results(mutableListOf(Result.Success())), "Could not find $newerContractPath at HEAD")
    } catch(e: FileNotFoundException) {
        Outcome(Results(mutableListOf(Result.Success())), "Could not find $newerContractPath on the file system")
    }
}

internal fun backwardCompatibleCommit(
    contractPath: String,
    newerCommit: String,
    olderCommit: String,
    git: GitCommand,
    numberOfThreads: Int? = null
): Outcome<Results> {
    val (gitRoot, relativeContractPath) = git.relativeGitPath(contractPath)

    val partial = getFileContentAtSpecifiedCommit(gitRoot)(relativeContractPath)(contractPath)

    return partial(newerCommit).onSuccess { newerGherkin ->
        partial(olderCommit).onSuccess { olderGherkin ->
            Outcome(testBackwardCompatibility(parseGherkinStringToFeature(olderGherkin), parseGherkinStringToFeature(newerGherkin), numberOfThreads ?: 1))
        }
    }
}

internal fun getOlderFeature(newerContractPath: String, git: GitCommand): Outcome<Feature> {
    if(!git.fileIsInGitDir(newerContractPath))
        return Outcome(null, "Older contract file must be provided, or the file must be in a git directory")

    val(contractGit, relativeContractPath) = git.relativeGitPath(newerContractPath)
    return Outcome(parseGherkinStringToFeature(contractGit.show("HEAD", relativeContractPath)))
}

internal data class CompatibilityOutput(val exitCode: Int, val message: String)

internal fun compatibilityMessage(results: Outcome<Results>): CompatibilityOutput {
    return when {
        results.result == null -> CompatibilityOutput(1, results.errorMessage)
        results.result.success() -> CompatibilityOutput(0, results.errorMessage.ifEmpty { "The newer contract is backward compatible" })
        else -> CompatibilityOutput(1, compatibilityReport(results.result, "The newer contract is NOT backward compatible"))
    }
}

internal fun checkCompatibility(compatibilityCheck: () -> Outcome<Results>): CompatibilityOutput =
    try {
        val results = compatibilityCheck()
        compatibilityMessage(results)
    } catch(e: Throwable) {
        CompatibilityOutput(1, "Could not run backwad compatibility check, got exception\n${exceptionCauseMessage(e)}")
    }
