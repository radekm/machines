package cz.radekm.machines

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Tests in this file are have Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
// Use of them is governed by the Apache 2.0 license.
// Original file:  kotlin/libraries/stdlib/test/coroutines/SequenceBuilderTest.kt
//
// I slightly modified the original code:
// - Replacing `sequence {` by `generator {`.
// - Replacing `sequence<` by `generator<`.
// - Replacing `return@sequence` by `return@generator`.
// - Replacing `sequenceOf` by `generatorOf`.
// - To compile I needed to add 1 type annotation for when calling `generator`.

class SequenceBuilderTestForGenerators {
    fun <A> Generator<A>.iterator() = object : Iterator<A> {
        val machine = toMachine()
        var next = Cell<A>()

        fun ensureNext() {
            if (next.isEmpty()) {
                while (machine.state != STOPPED && machine.output.isEmpty()) {
                    machine.resume()
                }
                if (machine.output.isFull()) {
                    next.put(machine.output.take())
                }
            }
        }

        override fun hasNext(): Boolean {
            ensureNext()
            return next.isFull()
        }

        override fun next(): A {
            ensureNext()
            if (next.isEmpty())
                throw NoSuchElementException()
            else return next.take()
        }

    }

    @Test
    fun testSimple() {
        val result = generator {
            for (i in 1..3) {
                yield(2 * i)
            }
        }

        assertEquals(listOf(2, 4, 6), result.toList())
        // Repeated calls also work
        assertEquals(listOf(2, 4, 6), result.toList())
    }

    @Test
    fun testCallHasNextSeveralTimes() {
        val result = generator {
            yield(1)
        }

        val iterator = result.iterator()

        assertTrue(iterator.hasNext())
        assertTrue(iterator.hasNext())
        assertTrue(iterator.hasNext())

        assertEquals(1, iterator.next())

        assertFalse(iterator.hasNext())
        assertFalse(iterator.hasNext())
        assertFalse(iterator.hasNext())

        assertFailsWith<NoSuchElementException> { iterator.next() }
    }

    @Test
    fun testManualIteration() {
        val result = generator {
            yield(1)
            yield(2)
            yield(3)
        }

        val iterator = result.iterator()

        assertTrue(iterator.hasNext())
        assertTrue(iterator.hasNext())
        assertEquals(1, iterator.next())

        assertTrue(iterator.hasNext())
        assertTrue(iterator.hasNext())
        assertEquals(2, iterator.next())

        assertEquals(3, iterator.next())

        assertFalse(iterator.hasNext())
        assertFalse(iterator.hasNext())

        assertFailsWith<NoSuchElementException> { iterator.next() }

        assertEquals(1, result.iterator().next())
    }

    @Test
    fun testEmptySequence() {
        val result = generator<Int> {}
        val iterator = result.iterator()

        assertFalse(iterator.hasNext())
        assertFalse(iterator.hasNext())

        assertFailsWith<NoSuchElementException> { iterator.next() }
    }

    @Test
    fun testLaziness() {
        var sharedVar = -2
        val result = generator {
            while (true) {
                when (sharedVar) {
                    -1 -> return@generator
                    -2 -> error("Invalid state: -2")
                    else -> yield(sharedVar)
                }
            }
        }

        val iterator = result.iterator()

        sharedVar = 1
        assertTrue(iterator.hasNext())
        assertEquals(1, iterator.next())

        sharedVar = 2
        assertTrue(iterator.hasNext())
        assertEquals(2, iterator.next())

        sharedVar = 3
        assertTrue(iterator.hasNext())
        assertEquals(3, iterator.next())

        sharedVar = -1
        assertFalse(iterator.hasNext())
        assertFailsWith<NoSuchElementException> { iterator.next() }
    }

    // FIXME This test fails. Probably because non-existent exception propagation.
    @Test
    fun testExceptionInCoroutine() {
        var sharedVar = -2
        val result = generator {
            while (true) {
                when (sharedVar) {
                    -1 -> return@generator
                    -2 -> throw UnsupportedOperationException("-2 is unsupported")
                    else -> yield(sharedVar)
                }
            }
        }

        val iterator = result.iterator()

        sharedVar = 1
        assertEquals(1, iterator.next())

        sharedVar = -2
        assertFailsWith<UnsupportedOperationException> { iterator.hasNext() }
        assertFailsWith<IllegalStateException> { iterator.hasNext() }
        assertFailsWith<IllegalStateException> { iterator.next() }
    }

    @Test
    fun testParallelIteration() {
        var inc = 0
        val result = generator {
            for (i in 1..3) {
                inc++
                yield(inc * i)
            }
        }

        assertEquals(listOf(Pair(1, 2), Pair(6, 8), Pair(15, 18)), result.zip(result).toList())
    }

    @Test
    fun testYieldAllIterator() {
        val result = generator {
            yieldAll(listOf(1, 2, 3).iterator())
        }
        assertEquals(listOf(1, 2, 3), result.toList())
    }

    @Test
    fun testYieldAllSequence() {
        val result = generator<Int> {
            yieldAll(generatorOf(1, 2, 3))
        }
        assertEquals(listOf(1, 2, 3), result.toList())
    }

    @Test
    fun testYieldAllCollection() {
        val result = generator {
            yieldAll(listOf(1, 2, 3))
        }
        assertEquals(listOf(1, 2, 3), result.toList())
    }

    @Test
    fun testYieldAllCollectionMixedFirst() {
        val result = generator {
            yield(0)
            yieldAll(listOf(1, 2, 3))
        }
        assertEquals(listOf(0, 1, 2, 3), result.toList())
    }

    @Test
    fun testYieldAllCollectionMixedLast() {
        val result = generator {
            yieldAll(listOf(1, 2, 3))
            yield(4)
        }
        assertEquals(listOf(1, 2, 3, 4), result.toList())
    }

    @Test
    fun testYieldAllCollectionMixedBoth() {
        val result = generator {
            yield(0)
            yieldAll(listOf(1, 2, 3))
            yield(4)
        }
        assertEquals(listOf(0, 1, 2, 3, 4), result.toList())
    }

    @Test
    fun testYieldAllCollectionMixedLong() {
        val result = generator {
            yield(0)
            yieldAll(listOf(1, 2, 3))
            yield(4)
            yield(5)
            yieldAll(listOf(6))
            yield(7)
            yieldAll(listOf())
            yield(8)
        }
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7, 8), result.toList())
    }

    @Test
    fun testYieldAllCollectionOneEmpty() {
        val result = generator<Int> {
            yieldAll(listOf())
        }
        assertEquals(listOf(), result.toList())
    }

    @Test
    fun testYieldAllCollectionManyEmpty() {
        val result = generator<Int> {
            yieldAll(listOf())
            yieldAll(listOf())
            yieldAll(listOf())
        }
        assertEquals(listOf(), result.toList())
    }

    @Test
    fun testYieldAllSideEffects() {
        val effects = arrayListOf<Any>()
        val result = generator {
            effects.add("a")
            yieldAll(listOf(1, 2))
            effects.add("b")
            yieldAll(listOf())
            effects.add("c")
            yieldAll(listOf(3))
            effects.add("d")
            yield(4)
            effects.add("e")
            yieldAll(listOf())
            effects.add("f")
            yield(5)
        }

        for (res in result.iterator()) {
            effects.add("(") // marks step start
            effects.add(res)
            effects.add(")") // marks step end
        }
        assertEquals(
                listOf(
                        "a",
                        "(", 1, ")",
                        "(", 2, ")",
                        "b", "c",
                        "(", 3, ")",
                        "d",
                        "(", 4, ")",
                        "e", "f",
                        "(", 5, ")"
                ),
                effects.toList()
        )
    }

    @Test
    fun testInfiniteYieldAll() {
        val values = iterator {
            while (true) {
                yieldAll((1..5).map { it })
            }
        }

        var sum = 0
        repeat(10) {
            sum += values.next() //.also(::println)
        }
        assertEquals(30, sum)
    }
}
