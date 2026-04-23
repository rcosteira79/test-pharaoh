package com.androidtestagent.signatures

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals

class IntegrationTest {
    private fun runMain(args: Array<String>): Pair<Int, String> {
        val err = ByteArrayOutputStream()
        val prevErr = System.err
        System.setErr(PrintStream(err))
        var code = 0
        try {
            main(args)
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
}
