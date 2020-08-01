package cz.radekm.machines

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeneratorTest {
    @Test
    fun `empty generator`() {
        val g = generator<Nothing> {
        }
        val expected = emptyList<Nothing>()
        val actual = g.toList()
        assertEquals(expected, actual)
    }

    @Test
    fun `generator generates nulls`() {
        val g = generator {
            yield("foo")
            yield(null)
            yield("bar")
        }
        val expected = listOf("foo", null, "bar")
        val actual = g.toList()
        assertEquals(expected, actual)
    }

    // This is the main reason why generators exist.
    @Test
    fun `generator runs code in finally block`() {
        var finallyExecuted = false
        val g = generator {
            try {
                yield(1)
                yield(2)
                yield(3)
            } finally {
                finallyExecuted = true
            }
        }.take(2)
        val expected = listOf(1, 2)
        val actual = g.toList()
        assertEquals(expected, actual)
        assertTrue(finallyExecuted)
    }

    // This is the main reason why generators exist.
    @Test
    fun `sequence doesn't run code in finally block`() {
        var finallyExecuted = false
        val seq = sequence {
            try {
                yield(1)
                yield(2)
                yield(3)
            } finally {
                finallyExecuted = true
            }
        }.take(2)
        val expected = listOf(1, 2)
        val actual = seq.toList()
        assertEquals(expected, actual)
        assertTrue(!finallyExecuted)
    }
}
