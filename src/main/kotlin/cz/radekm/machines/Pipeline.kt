package cz.radekm.machines

import kotlin.experimental.ExperimentalTypeInference

enum class PauseReason {
    CREATED,
    INPUT,
    OUTPUT,
    EARLY_OUTPUT
}

class Tee<I, O, E>(
        var pauseReason: PauseReason,
        var input: RingBuffer<I>,
        var output: RingBuffer<O>,
        var earlyOutput: Appendable<E>
) : CanResume {
    override fun canResume() = when (pauseReason) {
        PauseReason.CREATED -> true
        PauseReason.INPUT -> !input.isEmpty()
        PauseReason.OUTPUT -> !output.isFull()
        PauseReason.EARLY_OUTPUT -> !earlyOutput.isFull()
    }
}

typealias TeeScope<I, O, E> = MachineScope<Tee<I, O, E>>

@OptIn(ExperimentalTypeInference::class)
fun <I, O, E> tee(
        @BuilderInference block: suspend TeeScope<I, O, E>.() -> Unit
): Machine<Tee<I, O, E>> = machine(Tee<I, O, E>(PauseReason.CREATED, RingBuffer(), RingBuffer(), RingBuffer()), block)

@OptIn(ExperimentalTypeInference::class)
fun <I, O> pipe(
        @BuilderInference block: suspend TeeScope<I, O, Nothing>.() -> Unit
): Machine<Tee<I, O, Nothing>> = tee(block)

@OptIn(ExperimentalTypeInference::class)
fun <I, O, E> teeTransform(
        @BuilderInference block: suspend TeeScope<I, O, E>.(I) -> Unit
): Machine<Tee<I, O, E>> = tee<I, O, E> {
    while (true) {
        val i = await()
        block(i)
    }
}

@OptIn(ExperimentalTypeInference::class)
fun <I, O> pipeTransform(
        @BuilderInference block: suspend TeeScope<I, O, Nothing>.(I) -> Unit
): Machine<Tee<I, O, Nothing>> = pipe<I, O> {
    while (true) {
        val i = await()
        block(i)
    }
}

suspend fun <I, O, E> TeeScope<I, O, E>.await(): I {
    while (machineContext.input.isEmpty()) {
        machineContext.pauseReason = PauseReason.INPUT
        pause()
    }
    return machineContext.input.take()
}

suspend fun <I, O, E> TeeScope<I, O, E>.yield(o: O) {
    while (machineContext.output.isFull()) {
        machineContext.pauseReason = PauseReason.OUTPUT
        pause()
    }
    machineContext.output.append(o)
}

suspend fun <I, O, E> TeeScope<I, O, E>.yieldEarly(e: E) {
    while (machineContext.earlyOutput.isFull()) {
        machineContext.pauseReason = PauseReason.EARLY_OUTPUT
        pause()
    }
    machineContext.earlyOutput.append(e)
}

class PipelineBuilder<I> private constructor(
        private val machines: MutableList<Machine<Tee<*, *, *>>>,
        private val setOutputOfLastMachine: (RingBuffer<I>) -> Unit
) {
    constructor() : this(mutableListOf(), {})

    fun <O> attach(pipe: Machine<Tee<I, O, Nothing>>): PipelineBuilder<O> {
        setOutputOfLastMachine(pipe.machineContext.input)
        machines.add(pipe as Machine<Tee<*, *, *>>)
        return PipelineBuilder<O>(machines) { pipe.machineContext.output = it }
    }

    fun <O, E> attach(tee: Machine<Tee<I, O, E>>, earlyOutput: Appendable<E>): PipelineBuilder<O> {
        tee.machineContext.earlyOutput = earlyOutput
        setOutputOfLastMachine(tee.machineContext.input)
        machines.add(tee as Machine<Tee<*, *, *>>)
        return PipelineBuilder<O>(machines) { tee.machineContext.output = it }
    }

    fun build(): List<Machine<Tee<*, *, *>>> = machines
}
