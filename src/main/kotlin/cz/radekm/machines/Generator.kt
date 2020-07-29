package cz.radekm.machines

import kotlin.experimental.ExperimentalTypeInference

typealias GeneratorScope<O> = TeeScope<Nothing, O, Nothing>

/**
 * Sequences where `finally` finally works.
 */
interface Generator<A> {
    // TODO Somehow fix type inference for `attach`.
    fun <B> attach(makeMachine: () -> Machine<Tee<A, B, Nothing>>): Generator<B>
}

private class GeneratorImpl<A>(val makePipelineBuilder: () -> PipelineBuilder<A>) : Generator<A> {
    override fun <B> attach(makeMachine: () -> Machine<Tee<A, B, Nothing>>): Generator<B> = GeneratorImpl {
        makePipelineBuilder().attach(makeMachine())
    }
}

@OptIn(ExperimentalTypeInference::class)
fun <A> generator(@BuilderInference block: suspend GeneratorScope<A>.() -> Unit): Generator<A> =
        GeneratorImpl<Nothing> { PipelineBuilder() }.attach { pipe(block) }

fun <I, O> Generator<I>.map(f: (I) -> O): Generator<O> = attach<O> {
    pipeTransform { yield(f(it)) }
}

fun <A> Generator<A>.take(i: Int): Generator<A> = attach<A> {
    pipe {
        repeat(i) { yield(await()) }
    }
}

fun <A> Generator<A>.forEach(f: (A) -> Unit): Unit {
    val g = attach<Nothing> { pipeTransform { f(it) } } as GeneratorImpl
    val pipeline = g.makePipelineBuilder().build()
    try { runMany(pipeline) }
    finally {
        shutDownMany(pipeline)
    }
}

fun main() {
    // TODO Remove buffering to make behaviour which is easier to understand.
    // FIXME Type inference.
    generator<Int> {
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
