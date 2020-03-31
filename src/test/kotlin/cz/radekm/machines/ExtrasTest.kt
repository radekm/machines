package cz.radekm.machines

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class ExtrasTest {
    @Test
    fun `feed not enough elements`() {
        val m = machine<String, Int> {
            await()
            await()
        }
        m.start()

        val fed = m.feedManyCb(listOf("a")) { fail("must not be called") }

        assertEquals(1, fed)
        assertEquals(NEEDS_INPUT, m.state)
        assertEquals(null, m.stoppedBy)
    }

    @Test
    fun `feed enough elements`() {
        val m = machine<String, Int> { await() }
        m.start()

        val fed = m.feedManyCb(listOf("a", "b", "c")) { fail("must not be called") }

        assertEquals(1, fed)
        assertEquals(STOPPED, m.state)
        assertEquals(null, m.stoppedBy)
    }

    @Test
    fun `feed machine which doesn't need input`() {
        val m = machine<String, Int> {
            yield(1)
            yield(2)
            yield(3)
        }
        m.start()

        val output = mutableListOf<Int>()
        val fed = m.feedManyCb(listOf("a", "b", "c")) { output += it }

        assertEquals(0, fed)
        assertEquals(STOPPED, m.state)
        assertEquals(null, m.stoppedBy)
        assertEquals(listOf(1, 2, 3), output)
    }

    @Test
    fun `feed machine which for 1 input produces 2 outputs`() {
        val m = machine<String, Int> {
            while(true) {
                val s = await()
                yield(s.filter { it.isLowerCase() }.length)
                yield(s.filter { it.isUpperCase() }.length)
            }
        }
        m.start()

        val output = mutableListOf<Int>()
        val fed = m.feedManyCb(listOf("Hello World!", "YouTube")) { output += it }

        assertEquals(2, fed)
        assertEquals(NEEDS_INPUT, m.state)
        assertEquals(null, m.stoppedBy)
        assertEquals(listOf(8, 2, 5, 2), output)
    }

    @Test
    fun `drain machine which awaits 1 input`() {
        val m = machine<String, Int> {
            await()
        }
        m.start()

        m.drainCb { fail("must not be called") }

        assertEquals(STOPPED, m.state)
        assertTrue(m.stoppedBy is StopMachineException)
    }

    @Test
    fun `drain machine which for 1 input produces 1 output`() {
        val m = machine<String, Int> {
            while (true) {
                yield(await().length)
            }
        }
        m.start()
        m.feed("Hi")

        val output = mutableListOf<Int>()
        m.drainCb { output += it }

        assertEquals(STOPPED, m.state)
        assertTrue(m.stoppedBy is StopMachineException)
        assertEquals(listOf(2), output)
    }

    @Test
    fun `drain machine yields values when stopped`() {
        val reversingMachine = machine<String, String> {
            val buffer = mutableListOf<String>()
            try {
                while (true) {
                    buffer += await()
                }
            } finally {
                buffer.asReversed().forEach { yield(it) }
            }
        }
        reversingMachine.start()
        reversingMachine.feed("hello")
        reversingMachine.feed("world")
        reversingMachine.feed("!")

        val output = mutableListOf<String>()
        reversingMachine.drainCb { output += it }

        assertEquals(STOPPED, reversingMachine.state)
        assertTrue(reversingMachine.stoppedBy is StopMachineException)
        assertEquals(listOf("!", "world", "hello"), output)
    }

    @Test
    fun `drain machine which needs multiple calls to stop to really stop`() {
        val m = machine<String, Int> {
            try {
                yield(-1)
                yield(await().length)
            } catch (e: StopMachineException) {
                try {
                    yield(-2)
                    yield(await().length)
                } catch (e: StopMachineException) {
                    yield(-3)
                    throw RuntimeException()
                }
            }
        }
        m.start()

        val output = mutableListOf<Int>()
        m.drainCb { output += it }

        assertEquals(STOPPED, m.state)
        assertTrue(m.stoppedBy is RuntimeException)
        assertEquals(listOf(-1, -2, -3), output)
    }
}
