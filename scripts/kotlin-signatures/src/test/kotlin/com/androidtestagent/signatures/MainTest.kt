package com.androidtestagent.signatures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainTest {
    @Test
    fun `smoke - empty input returns empty string`() {
        assertEquals("", stripBodies(""))
    }

    @Test
    fun `top-level function body is stripped`() {
        val input =
            """
            fun add(a: Int, b: Int): Int {
                return a + b
            }
            """.trimIndent()

        val output = stripBodies(input)

        assertTrue(output.contains("fun add(a: Int, b: Int): Int"))
        assertFalse(output.contains("return a + b"))
    }

    @Test
    fun `member function body is stripped`() {
        val input =
            """
            class Greeter {
                fun hello(name: String): String {
                    return "Hello, ${'$'}name"
                }
            }
            """.trimIndent()

        val output = stripBodies(input)

        assertTrue(output.contains("class Greeter"))
        assertTrue(output.contains("fun hello(name: String): String"))
        assertFalse(output.contains("return \"Hello"))
    }

    @Test
    fun `nested (local) function inside an outer body does not crash the stripper`() {
        val input =
            """
            fun outer() {
                fun inner(): Int {
                    return 1
                }
                println(inner())
            }
            """.trimIndent()

        // The outer's body is stripped. We don't assert anything about the inner
        // (it's gone either way because the outer's body removal includes it).
        // The key property: stripBodies completes without an exception.
        val output = stripBodies(input)

        assertTrue(output.contains("fun outer()"))
    }
}
