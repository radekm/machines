package cz.radekm.machines

import kotlin.experimental.ExperimentalTypeInference

// TODO Exception handling and propagation through generators!!!
//      We should define behavior for special exception `StopMachineException`
//      and also for other critical and non-critical exceptions.
// Generally functions like `map`, `flatMap`, `forEach` should not catch exception
// from the function given as their parameter. Such exception should propagate.
// If any function/combinator operates on machines it should propagate exception
// from machine and ensure that machine is stopped.
// We should not use `finally` because it could suppress original exception.

// region Extensions for machines

val <A> Machine<Cell<A>>.output
        get() = machineContext

fun <A> Machine<Cell<A>>.await(): Option<A> {
    while (state != STOPPED && output.isEmpty()) {
        resume()
    }
    // TODO Should we propagate `StopMachineException`? Probably yes, because we're not cancelling.
    //      But this exception may be later ignored if closing a machine - maybe we should wrap it.
    stoppedBy?.let { throw it }

    if (output.isEmpty()) {
        return None
    }

    return Some(output.take())
}

inline fun <A> Machine<Cell<A>>.forEachAwaited(block: (A) -> Unit) {
    do {
        while (state != STOPPED && output.isEmpty()) {
            resume()
        }
        // TODO Should we propagate `StopMachineException` from await?
        stoppedBy?.let { throw it }

        if (output.isFull()) {
            block(output.take())
        }
    } while (state != STOPPED)
}

/**
 * Stop machine and propagate exception.
 */
fun <A> Machine<Cell<A>>.close() {
    while (state != STOPPED) {
        output.clear()
        cancel()
    }
    stoppedBy
            ?.takeUnless { it is StopMachineException }
            ?.let { throw it }
}

// endregion

interface GeneratorScope<O> {
    suspend fun yield(o: O)

    suspend fun yieldAll(iterator: Iterator<O>) {
        while (iterator.hasNext()) {
            yield(iterator.next())
        }
    }

    // `yieldAll` is not primitive but unfortunately making it extension function
    // results in much worse type inference :-(

    suspend fun yieldAll(elements: Iterable<O>) {
        if (elements is Collection && elements.isEmpty()) return
        return yieldAll(elements.iterator())
    }

    suspend fun yieldAll(sequence: Sequence<O>) {
        return yieldAll(sequence.iterator())
    }

    // TODO Is this correct?
    suspend fun yieldAll(generator: Generator<O>) {
        generator.withMachine { machine ->
            machine.forEachAwaited {
                yield(it)
            }
        }
    }
}

interface GeneratorTransformScope<I, O> : GeneratorScope<O> {
    fun await(): Option<I>
}

private class GeneratorScopeImpl<O>(private val parentScope: MachineScope<Cell<O>>) : GeneratorScope<O> {

    override suspend fun yield(o: O) {
        // TODO Consider extension property so we can use `output` instead of `machineContext`.
        if (parentScope.machineContext.isFull()) {
            System.err.println("Fatal error: Yielding when output cell is full!")
            error("Output cell is full")
        }
        parentScope.machineContext.put(o)
        parentScope.pause()
    }
}

private class GeneratorTransformScopeImpl<I, O>(
        private val parentScope: GeneratorScope<O>,
        private val inputMachine: Machine<Cell<I>>
): GeneratorTransformScope<I, O> {
    override fun await(): Option<I> = inputMachine.await()
    override suspend fun yield(o: O) = parentScope.yield(o)
}

class Generator<A>(private val block: suspend GeneratorScope<A>.() -> Unit) {
    @OptIn(ExperimentalTypeInference::class)
    fun <B> transform(@BuilderInference block: suspend GeneratorTransformScope<A, B>.() -> Unit): Generator<B> = Generator {
        withMachine { GeneratorTransformScopeImpl(this, it).block() }
    }

    // Not a primitive.
    // Defined inside [[Generator]] because only one type parameter needs to be specified when calling it.
    @OptIn(ExperimentalTypeInference::class)
    fun <B> transformItem(@BuilderInference block: suspend GeneratorTransformScope<A, B>.(A) -> Unit): Generator<B> = transform {
        while (true) {
            val a = await()
            if (a.isPresent) {
                block(a.get())
            } else {
                break
            }
        }
    }

    /**
     * Dangerous function. Don't forget to close the machine properly and propagate exceptions!
     *
     * Or use `withMachine` instead.
     */
    fun toMachine(): Machine<Cell<A>> = machine(Cell()) {
        GeneratorScopeImpl(this).block()
    }

    inline fun <R> withMachine(block: (Machine<Cell<A>>) -> R) {
        val machine = toMachine()
        // Use `use` from the library - it doesn't lose original exception
        // when a new one is thrown in `finally`.
        return AutoCloseable { machine.close() }.use {
            block(machine)
        }
    }

    // Not a primitive.
    fun run() {
        withMachine { machine ->
            while (machine.state != STOPPED) {
                machine.output.clear()
                machine.resume()
            }
        }
    }
}

@OptIn(ExperimentalTypeInference::class)
fun <O> generator(@BuilderInference block: suspend GeneratorScope<O>.() -> Unit): Generator<O> = Generator(block)

fun <A, B> Generator<A>.flatMap(f: (A) -> Generator<B>): Generator<B> = transformItem { a ->
    val generatorB = f(a)
    generatorB.withMachine { machineB ->
        machineB.forEachAwaited { yield(it) }
    }
}

// TODO Should be check that both generators generate same number of elements?
infix fun <A, B> Generator<A>.zip(other: Generator<B>): Generator<Pair<A, B>> = generator {
    withMachine { machineA ->
        other.withMachine { machineB ->
            while (true) {
                val a = machineA.await()
                val b = machineB.await()
                if (a.isPresent && b.isPresent) {
                    yield(a.get() to b.get())
                } else {
                    break
                }
            }
        }
    }
}

// This doesn't support inlining.
//fun <A> Generator<A>.forEach(f: (A) -> Unit) = transformItem<Nothing> { f(it) }.run()

inline fun <A> Generator<A>.forEach(f: (A) -> Unit) {
    withMachine { machine ->
        machine.forEachAwaited(f)
    }
}
