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

const val CREATED: State = 0
const val RUNNING: State = 1
const val NEEDS_INPUT: State = 2
const val HAS_OUTPUT: State = 3
const val STOPPED: State = 4

@OptIn(ExperimentalTypeInference::class)
fun <I, O> machine(@BuilderInference block: suspend MachineScope<I, O>.() -> Unit): Machine<I, O> {
    val m = MachineBuilder<I, O>()
    m.nextStepAfterCreation = block.createCoroutineUnintercepted(receiver = m, completion = m)
    return m
}

interface Machine<I, O> {
    val state: State
    val stoppedBy: Throwable?
    fun start()
    fun feed(input: I)
    fun take(): O
    fun stop()
}

interface MachineScope<I, O> {
    suspend fun await(): I
    suspend fun yield(output: O)
}

class StopMachineException : Exception()

private class MachineBuilder<I, O>() : Machine<I, O>, MachineScope<I, O>, Continuation<Unit> {
    // When modifying internal state we always switch to `RUNNING`.
    // We switch back from `RUNNING` only after all other variables
    // have been updated to correct values.
    override var state: State = CREATED
    override var stoppedBy: Throwable? = null

    // Value produced by `yield`. Non-null iff `state == HAS_OUTPUT`.
    private var valueToTake: O? = null

    // Continuations.
    // When `state in [RUNNING, STOPPED]` all of 3 continuations are `null`
    // else exactly one continuation is defined.
    internal var nextStepAfterCreation: Continuation<Unit>? = null
    private var nextStepAfterAwait: Continuation<I>? = null
    private var nextStepAfterYield: Continuation<Unit>? = null

    // ------------------------------------------------------------------------------

    // region Implementation of Machine<I, O>

    override fun start() {
        check(state == CREATED) { "start: Invalid state" }
        // Before modifying internal state we should switch to `RUNNING`.
        state = RUNNING
        val step = nextStepAfterCreation!!
        nextStepAfterCreation = null

        // This call returns when the continuation suspends (because of call to `await` or `yield`)
        // or terminates (normally or because of exception).
        //
        // If this continuation suspends then state will be `NEEDS_INPUT` or `HAS_INPUT`.
        // If it terminates then `resumeWith` sets the state to `STOPPED`.
        step.resume(Unit)
    }

    override fun feed(input: I) {
        check(state == NEEDS_INPUT) { "feed: Invalid state" }
        state = RUNNING
        val step = nextStepAfterAwait!!
        nextStepAfterAwait = null

        // Comment above `resume` in `start` holds here too.
        step.resume(input)
    }

    override fun take(): O {
        check(state == HAS_OUTPUT) { "take: Invalid state" }
        state = RUNNING
        val output = valueToTake!!
        valueToTake = null
        val step = nextStepAfterYield!!
        nextStepAfterYield = null

        // Comment above `resume` in `start` holds here too.
        step.resume(Unit)

        return output
    }

    override fun stop() {
        val origState = state
        check(origState == CREATED || origState == NEEDS_INPUT || origState == HAS_OUTPUT) {
            "stop: Invalid state"
        }

        var step: Continuation<*>?
        state = RUNNING // Switch to running before modifying internal state.
        when (origState) {
            CREATED -> {
                step = nextStepAfterCreation!!
                nextStepAfterCreation = null
            }
            NEEDS_INPUT -> {
                step = nextStepAfterAwait!!
                nextStepAfterAwait = null
            }
            HAS_OUTPUT -> {
                // TODO Maybe we should log this event or call some callback?
                // Throw away value which was not taken.
                valueToTake = null
                step = nextStepAfterYield!!
                nextStepAfterYield = null
            }
            else -> error("Internal error: Impossible")
        }
        step.resumeWith(Result.failure(StopMachineException()))
    }

    // endregion

    // ------------------------------------------------------------------------------

    // region Implementation of MachineScope<I, O>

    override suspend fun await(): I {
        check(state == RUNNING) { "Internal error: Impossible" }

        state = NEEDS_INPUT
        return suspendCoroutineUninterceptedOrReturn { c ->
            nextStepAfterAwait = c
            COROUTINE_SUSPENDED
        }
    }

    override suspend fun yield(output: O) {
        check(state == RUNNING) { "Internal error: Impossible" }

        valueToTake = output
        state = HAS_OUTPUT
        return suspendCoroutineUninterceptedOrReturn { c ->
            nextStepAfterYield = c
            COROUTINE_SUSPENDED
        }
    }

    // endregion

    // ------------------------------------------------------------------------------

    // region Implementation of Continuation<Unit>

    // Completion continuation implementation
    override fun resumeWith(result: Result<Unit>) {
        check(state == RUNNING) { "Internal error: Impossible" }

        stoppedBy = result.exceptionOrNull()
        state = STOPPED
    }

    override val context: CoroutineContext
        get() = EmptyCoroutineContext

    // endregion
}
