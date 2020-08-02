package cz.radekm.machines

import kotlin.experimental.ExperimentalTypeInference

// TODO Exception handling and propagation through generators!!!
//      We should define behavior for special exceptions `StopMachineException` and `AwaitFailedException`
//      and also for other critical and non-critical exceptions.

// region Extensions for machines

val <A> Machine<Cell<A>>.output
        get() = machineContext

fun <A> Machine<Cell<A>>.await(): Option<A> {
    while (state != STOPPED && output.isEmpty()) {
        resume()
    }

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

// FIXME Make this return a same generator every time.
fun <A> emptyGenerator(): Generator<A> = generator {}

fun <A> generatorOf(vararg elements: A): Generator<A> = if (elements.isEmpty()) emptyGenerator() else generator {
    yieldAll(elements.iterator())
}

fun <A> Generator<A>.take(howMany: Int): Generator<A> = transform {
    repeat(howMany) {
        val a = await()
        if (a.isPresent) {
            yield(a.get())
        } else {
            return@repeat
        }
    }
}

fun <A, B> Generator<A>.flatMap(f: (A) -> Generator<B>): Generator<B> = transformItem { a ->
    val generatorB = f(a)
    generatorB.withMachine { machineB ->
        machineB.forEachAwaited { yield(it) }
    }
}

fun <A, B> Generator<A>.map(f: (A) -> B): Generator<B> = transformItem { yield(f(it)) }

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

fun <A> Generator<A>.forEach(f: (A) -> Unit) = transformItem<Nothing> { f(it) }.run()

fun <A> Generator<A>.toList(): List<A> {
    val result = mutableListOf<A>()
    forEach { result += it }
    return result
}

fun main() {
    generator {
        try {
            println("generator")
            yield(1)
            yield(2)
            yield(3)
        } finally {
            println("closing generator")
        }
    }.map { it + 1 }.take(2).forEach { println(it) }

    sequence {
        try {
            println("sequence")
            yield(1)
            yield(2)
            yield(3)
        } finally {
            println("closing sequence")
        }
    }.map { it + 1 }.take(2).forEach { println(it) }
}
