package cz.radekm.machines

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

@ExperimentalStdlibApi
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

    fun createTrimThenLenghtPipeline(): Pipeline<String, Int> {
        val trim = machine<String, String> {
            while (true) {
                yield(await().trim())
            }
        }
        val length = machine<String, Int> {
            while (true) {
                yield(await().length)
            }
        }
        return Pipeline.create(trim, length)
    }

    @Test
    fun `pipeline where every machine immediately outputs`() {
        val pipeline = createTrimThenLenghtPipeline()
        val trimMachine = pipeline.machines[0]
        val lengthMachine = pipeline.machines[1]
        pipeline.start()

        val feedOutput = mutableListOf<Int>()
        pipeline.feedCb(listOf("hello", "  dog"), mutableListOf(), mutableListOf(), { _, _ -> fail() }) {
            feedOutput += it
        }
        assertEquals(listOf(5, 3), feedOutput)
        assertEquals(trimMachine.state, NEEDS_INPUT)
        assertEquals(lengthMachine.state, NEEDS_INPUT)

        val drainOutput = mutableListOf<Int>()
        pipeline.drainCb(mutableListOf(), mutableListOf(), { _, _ -> fail() }) {
            drainOutput += it
        }
        assertEquals(listOf<Int>(), drainOutput)
        assertEquals(trimMachine.state, STOPPED)
        assertEquals(lengthMachine.state, STOPPED)
    }

    fun createBufferThenDoublePipeline(): Pipeline<Int, Int> {
        val buffer = machine<Int, Int> {
            val q = ArrayDeque<Int>()
            try {
                while (true) {
                    q.addLast(await())
                    while (q.size > 3) {
                        yield(q.removeFirst())
                    }
                }
            } finally {
                while (q.isNotEmpty()) {
                    yield(q.removeFirst())
                }
            }
        }
        val double = machine<Int, Int> {
            while (true) {
                yield(await() * 2)
            }
        }
        return Pipeline.create(buffer, double)
    }

    @Test
    fun `pipeline where machine buffers elements but outputs them when stopped`() {
        val pipeline = createBufferThenDoublePipeline()
        val bufferMachine = pipeline.machines[0]
        val doubleMachine = pipeline.machines[1]
        pipeline.start()

        val feedOutput = mutableListOf<Int>()
        pipeline.feedCb(listOf(1, 2, 3, 4, 5, 6), mutableListOf(), mutableListOf(), { _, _ -> fail() }) {
            feedOutput += it
        }
        assertEquals(listOf(2, 4, 6), feedOutput)
        assertEquals(bufferMachine.state, NEEDS_INPUT)
        assertEquals(doubleMachine.state, NEEDS_INPUT)

        val drainOutput = mutableListOf<Int>()
        pipeline.drainCb(mutableListOf(), mutableListOf(), { _, _ -> fail() }) {
            drainOutput += it
        }
        assertEquals(listOf(8, 10, 12), drainOutput)
        assertEquals(bufferMachine.state, STOPPED)
        assertEquals(doubleMachine.state, STOPPED)
    }

    // TODO Test piepeline where middle machine stops.
    // TODO Test piepeline where last machine stops.
    // TODO Test pipeline where 1st machine stops.
    // TODO Test pipeline with 3 machines where every machine has lost input.
    // TODO Test buffers which already contain elements.
    // TODO Test feedCb with same buffers.
}
