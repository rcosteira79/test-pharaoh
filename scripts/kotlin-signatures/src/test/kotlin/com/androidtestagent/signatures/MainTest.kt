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
    fun `sealed class branches survive`() {
        val input =
            """
            sealed class LoginError {
                data object InvalidCredentials : LoginError()
                data class Throttled(val retryAfter: Long) : LoginError()
                data object Network : LoginError()
            }
            """.trimIndent()

        val output = stripBodies(input)

        assertTrue(output.contains("sealed class LoginError"))
        assertTrue(output.contains("InvalidCredentials : LoginError()"))
        assertTrue(output.contains("data class Throttled(val retryAfter: Long)"))
        assertTrue(output.contains("Network : LoginError()"))
    }

    @Test
    fun `kdoc, annotations, generics preserved and default values dropped`() {
        val input =
            """
            /** Fetches a user. */
            @Deprecated("use v2")
            fun <T : Any> fetch(
                id: String,
                cache: Cache<T> = DefaultCache()
            ): Result<T> {
                return Result.success(default)
            }
            """.trimIndent()

        val output = stripBodies(input)

        assertTrue(output.contains("/** Fetches a user. */"))
        assertTrue(output.contains("@Deprecated(\"use v2\")"))
        assertTrue(output.contains("fun <T : Any> fetch"))
        assertTrue(output.contains("cache: Cache<T>"))
        assertFalse(output.contains("DefaultCache()"))
        assertFalse(output.contains("cache: Cache<T> ="))
    }

    @Test
    fun `single-expression function body is stripped`() {
        val input =
            """
            fun double(x: Int) = x * 2
            fun <T> id(t: T): T = t
            """.trimIndent()

        val output = stripBodies(input)

        assertTrue(output.contains("fun double(x: Int)"))
        assertTrue(output.contains("fun <T> id(t: T): T"))
        assertFalse(output.contains("x * 2"))
        assertFalse(output.contains("= t"))
    }

    @Test
    fun `delegated property delegate body is stripped`() {
        val input =
            """
            class Service {
                val logger by lazy { Logger("svc") }
                val cache: Cache by Delegates.observable(Cache.empty()) { _, _, _ -> Unit }
            }
            """.trimIndent()

        val output = stripBodies(input)

        assertTrue(output.contains("val logger"))
        assertTrue(output.contains("val cache: Cache"))
        assertFalse(output.contains("Logger(\"svc\")"))
        assertFalse(output.contains("Delegates.observable"))
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
        assertFalse(Regex("""\binit\s*\{""").containsMatchIn(output))
        assertFalse(output.contains("println"))
    }
}
