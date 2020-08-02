package cz.radekm.machines.experimental

import cz.radekm.machines.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PipeTest {
    @Test
    fun `pipe which doesn't do anything`() {
        val m = pipe<String, Int> {}

        assertMachinePaused(m, PauseReason.CREATED)

        m.resume()

        assertMachineStoppedNormally(m)
    }

    @Test
    fun `pipe which throws exception`() {
        val m = pipe<String, Int> { throw RuntimeException("foo") }

        assertMachinePaused(m, PauseReason.CREATED)

        m.resume()

        assertEquals(STOPPED, m.state)
        assertTrue(m.stoppedBy is RuntimeException)
        assertEquals("foo", m.stoppedBy!!.message)
    }

    @Test
    fun `pipe which yields one item`() {
        val m = pipe<String, Int> { yield(-3) }

        assertMachinePaused(m, PauseReason.CREATED)

        m.resume()

        assertMachineStoppedNormally(m)
        assertEquals(listOf(-3), m.machineContext.output.toList())
    }

    @Test
    fun `pipe which yields more items than buffer capacity`() {
        val m = pipe<String, Int> {
            repeat(DEFAULT_RING_BUFFER_CAPACITY + 1) {
                yield(12)
            }
        }

        assertMachinePaused(m, PauseReason.CREATED)

        m.resume()

        assertMachinePaused(m, PauseReason.OUTPUT)
        assertEquals(List(DEFAULT_RING_BUFFER_CAPACITY) { 12 }, m.machineContext.output.toList())

        m.machineContext.output.clear()
        m.resume()

        assertMachineStoppedNormally(m)
        assertEquals(listOf(12), m.machineContext.output.toList())
    }

    @Test
    fun `pipe which awaits one item`() {
        val m = pipe<String, Int> { await() }

        assertMachinePaused(m, PauseReason.CREATED)

        m.machineContext.input.append("foo")
        m.resume()

        assertMachineStoppedNormally(m)
        assertEquals(0, m.machineContext.input.size)
    }

    @Test
    fun `pipe which awaits more items than in buffer`() {
        val m = pipe<String, Int> { await() }

        assertMachinePaused(m, PauseReason.CREATED)

        m.resume()

        assertMachinePaused(m, PauseReason.INPUT)
    }

    @Test
    fun `pipe which does side-effect when starting`() {
        var msg = "hello"
        val m = pipe<String, Int> {
            msg = "world"
            await()
        }

        assertMachinePaused(m, PauseReason.CREATED)
        assertEquals("hello", msg)

        m.resume()

        assertMachinePaused(m, PauseReason.INPUT)
        assertEquals("world", msg)
    }

    @Test
    fun `cancel pipe before first resume`() {
        var msg = "hello"
        val m = pipe<String, Int> { msg = "world" }

        assertMachinePaused(m, PauseReason.CREATED)
        assertEquals("hello", msg)

        m.cancel()

        assertMachineCancelled(m)
        assertEquals("hello", msg)
    }

    @Test
    fun `cancel pipe when awaiting input`() {
        var msg = "hello"
        val m = pipe<String, Int> {
            msg = "world"
            await()
            msg = "!"
        }

        m.resume()
        m.cancel()

        assertMachineCancelled(m)
        assertEquals("world", msg)
    }

    @Test
    fun `pipe which catches StopMachineException and continues`() {
        var msg = "goo"
        val m = pipe<String, Int> {
            try {
                msg = await()
            } catch (_: StopMachineException) {
                msg = "exceptional $msg"
                msg = await()
            }
        }

        m.resume()

        assertMachinePaused(m, PauseReason.INPUT)
        assertEquals("goo", msg)

        m.cancel()

        assertMachinePaused(m, PauseReason.INPUT)
        assertEquals("exceptional goo", msg)

        m.machineContext.input.append("foo")
        m.resume()

        assertMachineStoppedNormally(m)
        assertEquals("foo", msg)
    }

    @Test
    fun `pipe which returns length for each given string`() {
        val m = pipe<String, Int> {
            while (true) {
                yield(await().length)
            }
        }

        assertMachinePaused(m, PauseReason.CREATED)

        m.machineContext.input.appendMany(listOf("hi", "what's", "up", "?"))
        m.resume()

        assertMachinePaused(m, PauseReason.INPUT)
        assertEquals(listOf(2, 6, 2, 1), m.machineContext.output.toList())

        m.cancel()

        assertMachineCancelled(m)
    }

    @Test
    fun `suspending function with side effect inside pipe`() {
        var lastString: String? = null

        suspend fun TeeScope<String, Int, Nothing>.lengthWithSideEffect() {
            lastString = await()
            yield(lastString!!.length)
        }

        val m = pipe<String, Int> {
            lastString = "hello"
            lengthWithSideEffect()
        }

        assertMachinePaused(m, PauseReason.CREATED)
        assertEquals(null, lastString)

        m.machineContext.input.append("hello")
        m.resume()

        assertMachineStoppedNormally(m)
        assertEquals("hello", lastString)
    }

    // TODO Test what happens when inappropriate method is called.
}
