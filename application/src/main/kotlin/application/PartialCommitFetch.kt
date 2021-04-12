package application

import `in`.specmatic.core.git.GitCommand
import `in`.specmatic.core.utilities.exceptionCauseMessage

val getFileContentAtSpecifiedCommit = {
    gitRoot: GitCommand -> { relativeContractPath: String -> { contractPath: String -> { commit: String ->
    try {
        Outcome(gitRoot.show(commit, relativeContractPath).trim())
    } catch (e: Throwable) {
        Outcome(null, "Could not load ${commit}:${contractPath} because of error:\n${exceptionCauseMessage(e)}")
    }
} } } }
