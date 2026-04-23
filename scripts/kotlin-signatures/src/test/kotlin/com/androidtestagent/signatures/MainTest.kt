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
        assertFalse(output.contains("Hello, "))
    }

    @Test
    fun `overlapping range from an outer body enclosing a local function does not throw`() {
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
        // The key property: stripBodies completes without an exception even when
        // the inner body range overlaps with already-deleted territory.
        val output = stripBodies(input)

        assertTrue(output.contains("fun outer()"))
    }

    @Test
    fun `multiple top-level functions all have bodies stripped`() {
        val input =
            """
            fun a(): Int {
                return 1
            }
            fun b(): Int {
                return 2
            }
            """.trimIndent()

        val output = stripBodies(input)

        assertTrue(output.contains("fun a(): Int"))
        assertTrue(output.contains("fun b(): Int"))
        assertFalse(output.contains("return 1"))
        assertFalse(output.contains("return 2"))
    }

    @Test
    fun `property initializer is stripped`() {
        val input =
            """
            class Config {
                val endpoint: String = "https://api.example.com"
                var retries: Int = 3
            }
            """.trimIndent()

        val output = stripBodies(input)

        assertTrue(output.contains("val endpoint: String"))
        assertTrue(output.contains("var retries: Int"))
        assertFalse(output.contains("https://api.example.com"))
        assertFalse(output.contains("= 3"))
        assertFalse(output.contains("endpoint: String ="))
        assertFalse(output.contains("retries: Int ="))
    }

    @Test
    fun `const val initializer is stripped`() {
        val input =
            """
            const val VERSION = "1.0"
            """.trimIndent()

        val output = stripBodies(input)

        assertTrue(output.contains("const val VERSION"))
        assertFalse(output.contains("1.0"))
    }

    @Test
    fun `property without initializer is preserved`() {
        val input =
            """
            abstract class Base {
                abstract val name: String
                val fixed: Int
            }
            """.trimIndent()

        val output = stripBodies(input)

        assertTrue(output.contains("abstract val name: String"))
        assertTrue(output.contains("val fixed: Int"))
    }

    @Test
    fun `init blocks are removed entirely`() {
        val input =
            """
            class Service {
                private val id = 0
                init {
                    println("booting")
                }
                fun serve() {}
            }
            """.trimIndent()

        val output = stripBodies(input)

        assertTrue(output.contains("class Service"))
        assertTrue(output.contains("fun serve()"))
        assertFalse(output.contains("init"))
        assertFalse(output.contains("println"))
    }
}
