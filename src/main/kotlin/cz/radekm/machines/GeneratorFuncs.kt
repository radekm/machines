package cz.radekm.machines


// FIXME Make this return a same generator every time.
fun <A> emptyGenerator(): Generator<A> = generator {}

fun <A> generatorOf(vararg elements: A): Generator<A> = if (elements.isEmpty()) emptyGenerator() else generator {
    yieldAll(elements.iterator())
}

fun <T : Any> generateGenerator(seed: T?, nextFunction: (T) -> T?): Generator<T> =
        if (seed == null) emptyGenerator()
        else generator {
            var s = seed
            while (s != null) {
                // Hmm, compiler warns that `!!` is unnecessary but when I remove it code doesn't compile :-(
                // It seems that `@BuilderInference` is broken.
                yield(s!!)
                s = nextFunction(s)
            }
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

fun <A> Generator<A>.drop(howMany: Int): Generator<A> = transform {
    repeat(howMany) {
        val a = await()
        if (a.isEmpty) {
            return@repeat
        }
    }
    while (true) {
        val a = await()
        if (a.isEmpty) {
            break
        }
        yield(a.get())
    }
}

fun <A, B> Generator<A>.map(f: (A) -> B): Generator<B> = transformItem { yield(f(it)) }

fun <A> Generator<A>.filter(pred: (A) -> Boolean): Generator<A> = transformItem {
    if (pred(it)) {
        yield(it)
    }
}

fun <A> Generator<A>.filterNot(pred: (A) -> Boolean): Generator<A> = filter { !pred(it) }

fun <T : Any> Generator<T?>.filterNotNull(): Generator<T> {
    @Suppress("UNCHECKED_CAST")
    return filterNot { it == null } as Generator<T>
}

fun <A> Generator<A>.filterIndexed(pred: (Int, A) -> Boolean): Generator<A> = transform {
    var index = 0
    while (true) {
        val a = await()
        if (a.isEmpty) {
            break
        }
        if (pred(index, a.get()))
            yield(a.get())
        index++
    }
}

fun <A> Generator<A>.toList(): List<A> {
    val result = mutableListOf<A>()
    forEach { result += it }
    return result
}

fun <A> Generator<A>.count(): Int {
    var result = 0
    forEach { result++ }
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
