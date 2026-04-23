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
}
