package cz.radekm.machines

import kotlin.experimental.ExperimentalTypeInference

// TODO Exception handling and propagation through generators!!!
//      We should define behavior for special exceptions `StopMachineException` and `AwaitFailedException`
//      and also for other critical and non-critical exceptions.

// region Extensions for machines

val <A> Machine<Cell<A>>.output
        get() = machineContext

class AwaitFailedException : Exception()

fun <A> Machine<Cell<A>>.await(): A {
    while (state != STOPPED && output.isEmpty()) {
        resume()
    }

    if (output.isEmpty()) {
        throw AwaitFailedException()
    }

    return output.take()
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
            ?.takeUnless { it is StopMachineException || it is AwaitFailedException }
            ?.let { throw it }
}

// endregion

// region Primitives

interface GeneratorScope<O> {
    suspend fun yield(o: O)
}

interface GeneratorTransformScope<I, O> : GeneratorScope<O> {
    fun await(): I
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
    override fun await(): I = inputMachine.await()
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
            block(a)
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

// endregion Primitives

fun <A> Generator<A>.take(howMany: Int): Generator<A> = transform {
    repeat(howMany) {
        yield(await())
    }
}

fun <A, B> Generator<A>.flatMap(f: (A) -> Generator<B>): Generator<B> = transformItem { a ->
    val generatorB = f(a)
    generatorB.withMachine { machineB ->
        // TODO Check whether this logic is correct.
        // When `machineB` stops `await` throws `AwaitFailedException`
        // which must be caught before it reaches `transformItem` and interrupts it.
        try {
            while (true) {
                yield(machineB.await())
            }
        } catch (e: AwaitFailedException) {}
    }
}

fun <A, B> Generator<A>.map(f: (A) -> B): Generator<B> = transformItem { yield(f(it)) }

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
