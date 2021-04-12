package application.test

import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.TestPlan
import kotlin.system.exitProcess

class ContractExecutionListener : TestExecutionListener {
    private var success: Int = 0
    private var failure: Int = 0

    private val failedLog: MutableList<String> = mutableListOf()

    override fun executionFinished(testIdentifier: TestIdentifier?, testExecutionResult: TestExecutionResult?) {
        if (listOf("SpecmaticJUnitSupport", "contractAsTest()", "JUnit Jupiter").any {
                    testIdentifier!!.displayName.contains(it)
                }) return

        println("${testIdentifier?.displayName} ${testExecutionResult?.status}")
        testExecutionResult?.status?.name?.equals("SUCCESSFUL").let {
            when (it) {
                false -> {
                    failure++

                    val message = testExecutionResult?.throwable?.get()?.message?.replace("\n", "\n\t")?.trimIndent()
                            ?: ""

                    val reason = "Reason: $message"
                    println("$reason\n\n")

                    val log = """"${testIdentifier?.displayName} ${testExecutionResult?.status}"
${reason.prependIndent("  ")}"""

                    failedLog.add(log)
                }
                else -> {
                    success++
                    println()
                }
            }
        }
    }

    override fun testPlanExecutionFinished(testPlan: TestPlan?) {
        println("Tests run: ${success + failure}, Failures: $failure")

        if (failedLog.isNotEmpty()) {
            println()
            println("Failed scenarios:")
            println(failedLog.distinct().joinToString(System.lineSeparator()) { it.prependIndent("  ") })
        }
    }

    fun exitProcess() {
        val exitStatus = when (failure != 0) {
            true -> 1
            false -> 0
        }

        exitProcess(exitStatus)
    }
}