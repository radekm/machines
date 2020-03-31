package cz.radekm.machines

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MachinesTest {
    @Test
    fun `machine which doesn't do anything`() {
        val m = machine<String, Int> {}

        assertEquals(CREATED, m.state)
        assertEquals(null, m.stoppedBy)

        m.start()

        assertEquals(STOPPED, m.state)
        assertEquals(null, m.stoppedBy)
    }

    @Test
    fun `machine which throws exception`() {
        val m = machine<String, Int> { throw RuntimeException("foo") }

        assertEquals(CREATED, m.state)
        assertEquals(null, m.stoppedBy)

        m.start()

        assertEquals(STOPPED, m.state)
        assertTrue(m.stoppedBy is RuntimeException)
        assertEquals("foo", m.stoppedBy!!.message)
    }

    @Test
    fun `machine which yields one item`() {
        val m = machine<String, Int> { yield(-3) }

        assertEquals(CREATED, m.state)
        assertEquals(null, m.stoppedBy)

        m.start()

        assertEquals(HAS_OUTPUT, m.state)
        assertEquals(null, m.stoppedBy)

        assertEquals(-3, m.take())

        assertEquals(STOPPED, m.state)
        assertEquals(null, m.stoppedBy)
    }

    @Test
    fun `machine which awaits one item and saves it to outside variable`() {
        var received: String? = null
        val m = machine<String, Int> { received = await() }

        assertEquals(CREATED, m.state)
        assertEquals(null, m.stoppedBy)
        assertEquals(null, received)

        m.start()

        assertEquals(NEEDS_INPUT, m.state)
        assertEquals(null, m.stoppedBy)
        assertEquals(null, received)

        m.feed("bar")

        assertEquals(STOPPED, m.state)
        assertEquals(null, m.stoppedBy)
        assertEquals("bar", received)
    }

    @Test
    fun `machine which does side-effect when starting`() {
        var msg = "hello"
        val m = machine<String, Int> {
            msg = "world"
            await()
        }

        assertEquals(CREATED, m.state)
        assertEquals(null, m.stoppedBy)
        assertEquals("hello", msg)

        m.start()

        assertEquals(NEEDS_INPUT, m.state)
        assertEquals(null, m.stoppedBy)
        assertEquals("world", msg)
    }

    @Test
    fun `stop machine before start`() {
        var msg = "hello"
        val m = machine<String, Int> { msg = "world" }

        assertEquals(CREATED, m.state)
        assertEquals(null, m.stoppedBy)
        assertEquals("hello", msg)

        m.stop()

        assertEquals(STOPPED, m.state)
        assertTrue(m.stoppedBy is StopMachineException)
        assertEquals("hello", msg)
    }

    @Test
    fun `stop machine during yield`() {
        var msg = "hello"
        val m = machine<String, Int> {
            msg = "world"
            yield(42)
            msg = "!"
        }

        m.start()

        assertEquals(HAS_OUTPUT, m.state)
        assertEquals(null, m.stoppedBy)
        assertEquals("world", msg)

        m.stop()

        assertEquals(STOPPED, m.state)
        assertTrue(m.stoppedBy is StopMachineException)
        assertEquals("world", msg)
    }

    @Test
    fun `stop machine during await`() {
        var msg = "hello"
        val m = machine<String, Int> {
            msg = "world"
            msg = await()
        }

        m.start()

        assertEquals(NEEDS_INPUT, m.state)
        assertEquals(null, m.stoppedBy)
        assertEquals("world", msg)

        m.stop()

        assertEquals(STOPPED, m.state)
        assertTrue(m.stoppedBy is StopMachineException)
        assertEquals("world", msg)
    }

    @Test
    fun `machine which catches StopMachineException and continues`() {
        var msg = "goo"
        val m = machine<String, Int> {
            try {
                msg = await()
            } catch (_: StopMachineException) {
                msg = "exceptional $msg"
                msg = await()
            }
        }

        m.start()

        assertEquals(NEEDS_INPUT, m.state)
        assertEquals(null, m.stoppedBy)
        assertEquals("goo", msg)

        m.stop()

        assertEquals(NEEDS_INPUT, m.state)
        assertEquals(null, m.stoppedBy)
        assertEquals("exceptional goo", msg)

        m.feed("foo")

        assertEquals(STOPPED, m.state)
        assertEquals(null, m.stoppedBy)
        assertEquals("foo", msg)
    }

    @Test
    fun `more complex machine which returns length for each given string`() {
        val m = machine<String, Int> {
            while (true) {
                yield(await().length)
            }
        }

        assertEquals(CREATED, m.state)
        assertEquals(null, m.stoppedBy)

        m.start()

        for ((input, expectedOutput) in listOf("hi" to 2, "what's" to 6, "up" to 2, "?" to 1)) {
            assertEquals(NEEDS_INPUT, m.state)
            assertEquals(null, m.stoppedBy)

            m.feed(input)

            assertEquals(HAS_OUTPUT, m.state)
            assertEquals(null, m.stoppedBy)
            assertEquals(expectedOutput, m.take())
        }

        m.stop()

        assertEquals(STOPPED, m.state)
        assertTrue(m.stoppedBy is StopMachineException)
    }

    @Test
    fun `suspending function with side effect inside machine`() {
        var lastString: String? = null

        suspend fun MachineScope<String, Int>.lengthWithSideEffect() {
            lastString = await()
            yield(lastString!!.length)
        }

        val m = machine<String, Int> {
            lastString = "hello"
            lengthWithSideEffect()
        }

        assertEquals(CREATED, m.state)
        assertEquals(null, m.stoppedBy)
        assertEquals(null, lastString)

        m.start()

        assertEquals(NEEDS_INPUT, m.state)
        assertEquals(null, m.stoppedBy)
        assertEquals("hello", lastString)

        m.feed("world")

        assertEquals(HAS_OUTPUT, m.state)
        assertEquals(null, m.stoppedBy)
        assertEquals("world", lastString)

        assertEquals("world".length, m.take())

        assertEquals(STOPPED, m.state)
        assertEquals(null, m.stoppedBy)
        assertEquals("world", lastString)
    }

    // TODO Test what happens when inappropriate method is called.
}
