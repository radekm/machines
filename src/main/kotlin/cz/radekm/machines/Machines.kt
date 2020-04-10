package cz.radekm.machines

import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.createCoroutineUnintercepted
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.resume
import kotlin.experimental.ExperimentalTypeInference

typealias State = Int

const val PAUSED: State = 0 // Initial state.
const val RUNNING: State = 1
const val STOPPED: State = 2

@OptIn(ExperimentalTypeInference::class)
fun <Ctx> machine(context: Ctx, @BuilderInference block: suspend MachineScope<Ctx>.() -> Unit): Machine<Ctx> {
    val m = MachineBuilder(context)
    m.nextStep = block.createCoroutineUnintercepted(receiver = m, completion = m)
    return m
}

interface Machine<Ctx> {
    val machineContext: Ctx
    val state: State
    val stoppedBy: Throwable?
    fun resume()
    fun cancel()
}

interface MachineScope<Ctx> {
    val machineContext: Ctx
    suspend fun pause()
}

class StopMachineException : Exception()

private class MachineBuilder<Ctx>(override val machineContext: Ctx) : Machine<Ctx>, MachineScope<Ctx>, Continuation<Unit> {
    // When modifying internal state we always switch to `RUNNING`.
    // We switch back from `RUNNING` only after all other variables
    // have been updated to correct values.
    override var state: State = PAUSED
    override var stoppedBy: Throwable? = null

    // `null` when `state in [RUNNING, STOPPED]`.
    internal var nextStep: Continuation<Unit>? = null

    // ------------------------------------------------------------------------------

    // region Implementation of Machine<Ctx>

    override fun resume() {
        check(state == PAUSED) { "start: Invalid state" }
        // Before modifying internal state we should switch to `RUNNING`.
        state = RUNNING
        val step = nextStep!!
        nextStep = null

        // This call returns when the continuation suspends (because of call to `pause`)
        // or terminates (normally or because of exception).
        //
        // If this continuation suspends then state will be `PAUSED`.
        // If it terminates then `resumeWith` sets the state to `STOPPED`.
        step.resume(Unit)
    }

    override fun cancel() {
        check(state == PAUSED) { "stop: Invalid state" }

        state = RUNNING
        val step = nextStep!!
        nextStep = null

        step.resumeWith(Result.failure(StopMachineException()))
    }

    // endregion

    // ------------------------------------------------------------------------------

    // region Implementation of MachineScope<Ctx>

    override suspend fun pause() {
        check(state == RUNNING) { "Internal error: Impossible" }

        state = PAUSED
        return suspendCoroutineUninterceptedOrReturn<Unit> { c ->
            check(nextStep == null)
            nextStep = c
            COROUTINE_SUSPENDED
        }
    }

    // endregion

    // ------------------------------------------------------------------------------

    // region Implementation of Continuation<Unit>

    // Completion continuation implementation
    // Called when machine stops.
    override fun resumeWith(result: Result<Unit>) {
        check(state == RUNNING) { "Internal error: Impossible" }

        stoppedBy = result.exceptionOrNull()
        state = STOPPED
    }

    override val context: CoroutineContext
        get() = EmptyCoroutineContext

    // endregion
}
