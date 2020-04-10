package cz.radekm.machines

import kotlin.test.Test
import kotlin.test.assertEquals

class TeeTest {
    @Test
    fun `tee which yields only early output`() {
        val earlyOutput = RingBuffer<Boolean>()
        val m = tee<String, Int, Boolean>(earlyOutput) {
            yieldEarly(true)
            yieldEarly(false)
        }

        assertMachinePaused(m, PauseReason.CREATED)

        m.resume()

        assertMachineStoppedNormally(m)
        assertEquals(0, m.machineContext.output.size)
        assertEquals(listOf(true, false), earlyOutput.toList())
    }

    @Test
    fun `tee which yields both outputs`() {
        val earlyOutput = RingBuffer<Boolean>()
        val m = tee<String, Int, Boolean>(earlyOutput) {
            yieldEarly(true)
            yieldEarly(false)
            yield(-1)
        }

        assertMachinePaused(m, PauseReason.CREATED)

        m.resume()

        assertMachineStoppedNormally(m)
        assertEquals(listOf(-1), m.machineContext.output.toList())
        assertEquals(listOf(true, false), earlyOutput.toList())
    }

    @Test
    fun `tee which yields more early outputs than buffer capacity`() {
        val earlyOutput = RingBuffer<Boolean>()
        val m = tee<String, Int, Boolean>(earlyOutput) {
            repeat(DEFAULT_RING_BUFFER_CAPACITY + 1) {
                yieldEarly(true)
            }
        }

        assertMachinePaused(m, PauseReason.CREATED)

        m.resume()

        assertMachinePaused(m, PauseReason.EARLY_OUTPUT)
        assertEquals(List(DEFAULT_RING_BUFFER_CAPACITY) { true }, earlyOutput.toList())

        earlyOutput.clear()
        m.resume()

        assertMachineStoppedNormally(m)
        assertEquals(listOf(true), earlyOutput.toList())
    }
}