package cz.radekm.machines

import kotlin.test.assertEquals
import kotlin.test.assertTrue

fun <I, O, E> assertMachinePaused(m: Machine<Tee<I, O, E>>, pauseReason: PauseReason) {
    assertEquals(PAUSED, m.state, "state")
    assertEquals(null, m.stoppedBy, "stoppedBy")
    assertEquals(pauseReason, m.machineContext.pauseReason, "pauseReason")
}

fun <I, O, E> assertMachineStoppedNormally(m: Machine<Tee<I, O, E>>) {
    assertEquals(STOPPED, m.state, "state")
    assertEquals(null, m.stoppedBy, "stoppedBy")
}

fun <I, O, E> assertMachineCancelled(m: Machine<Tee<I, O, E>>) {
    assertEquals(STOPPED, m.state, "state")
    assertTrue(m.stoppedBy is StopMachineException, "stoppedBy")
}
