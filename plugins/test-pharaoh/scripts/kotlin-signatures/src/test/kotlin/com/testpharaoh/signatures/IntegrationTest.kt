package com.testpharaoh.signatures

import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals

@Execution(ExecutionMode.SAME_THREAD)
class IntegrationTest {
    private fun runMain(args: Array<String>): Pair<Int, String> {
        val err = ByteArrayOutputStream()
        val prevErr = System.err
        System.setErr(PrintStream(err))
        var code = 0
        try {
            run(args)
        } catch (e: ExitException) {
            code = e.code
        } finally {
            System.setErr(prevErr)
        }
        return code to err.toString()
    }

    @Test
    fun `exits 2 when no args`() {
        val (code, err) = runMain(emptyArray())
        assertEquals(2, code)
        assert(err.contains("usage:"))
    }

    @Test
    fun `exits 1 when file missing`() {
        val (code, err) = runMain(arrayOf("/nonexistent/file.kt"))
        assertEquals(1, code)
        assert(err.contains("file not found"))
    }

    @Test
    fun `exits 0 and prints stripped source on valid file`() {
        val tmp =
            java.io.File.createTempFile("sig", ".kt").apply {
                writeText(
                    """
                    fun greet(name: String): String {
                        return "Hello, ${'$'}name"
                    }
                    """.trimIndent(),
                )
                deleteOnExit()
            }

        // Capture stdout
        val out = java.io.ByteArrayOutputStream()
        val prevOut = System.out
        System.setOut(java.io.PrintStream(out))
        try {
            run(arrayOf(tmp.absolutePath))
        } finally {
            System.setOut(prevOut)
        }

        val output = out.toString()
        assert(output.contains("fun greet(name: String): String"))
        assert(!output.contains("Hello, "))
    }
}
