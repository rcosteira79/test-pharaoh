package com.androidtestagent.signatures

import kotlin.test.Test
import kotlin.test.assertEquals

class MainTest {
    @Test
    fun `smoke - empty input returns empty string`() {
        assertEquals("", stripBodies(""))
    }
}
