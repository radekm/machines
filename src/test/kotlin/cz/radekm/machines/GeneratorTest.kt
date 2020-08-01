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

    @Test
    fun `upstream generators are resumed AFTER downstream awaits more elements - this ensures that cleanup logic is executed AFTER the resources are used by downstream`() {
        val log = mutableListOf<String>()
        val g = generator {
            repeat(10) { x ->
                try {
                    log += "created $x"
                    yield(x)
                } finally { log += "freed $x" }
            }
        }.flatMap { x ->
            log += "generator in flatMap started using $x"
            generator {
                try {
                    yield(x)
                    yield(x)
                } finally { log += "generator in flatMap which was using $x stopped" }
            }.map { x ->
                log += "inner map can use $x too"
                x
            }
        }.take(5).map { x ->
            log += "outer map can also use $x"
            x
        }
        val expected = listOf(0, 0, 1, 1, 2)
        val expectedLog = listOf(
                // 0
                "created 0",
                "generator in flatMap started using 0",
                "inner map can use 0 too",
                "outer map can also use 0",
                "inner map can use 0 too",
                "outer map can also use 0",
                "generator in flatMap which was using 0 stopped",
                "freed 0",
                // 1
                "created 1",
                "generator in flatMap started using 1",
                "inner map can use 1 too",
                "outer map can also use 1",
                "inner map can use 1 too",
                "outer map can also use 1",
                "generator in flatMap which was using 1 stopped",
                "freed 1",
                // 2
                "created 2",
                "generator in flatMap started using 2",
                "inner map can use 2 too",
                "outer map can also use 2",
                "generator in flatMap which was using 2 stopped",
                "freed 2"
        )
        val actual = g.toList()
        assertEquals(expected, actual)
        assertEquals(expectedLog, log)
    }
}
