package cz.radekm.machines

interface Appendable<T> {
    fun isFull(): Boolean
    fun append(x: T)
}

fun <T> Appendable<T>.appendMany(xs: Iterable<T>) {
    for (x in xs)
        append(x)
}

const val DEFAULT_RING_BUFFER_CAPACITY = 10

class RingBuffer<T>(val capacity: Int = DEFAULT_RING_BUFFER_CAPACITY) : Appendable<T> {
    private val data = Array<Any?>(capacity) { null }
    private var start = 0
    var size = 0
        private set

    fun isEmpty() = size == 0
    override fun isFull() = size == capacity

    override fun append(x: T) {
        check(!isFull()) { "full" }
        val pos = (start + size) % capacity
        size++
        data[pos] = x
    }

    @Suppress("UNCHECKED_CAST")
    fun take(): T {
        check(!isEmpty()) { "empty" }
        val x = data[start]
        data[start] = null
        start = (start + 1) % capacity
        size--
        return x as T
    }

    fun clear() {
        for (i in data.indices)
            data[i] = null
        start = 0
        size = 0
    }

    @Suppress("UNCHECKED_CAST")
    fun toList(): List<T> {
        val list = ArrayList<T>(size)
        var pos = start
        var remaining = size
        while (remaining > 0) {
            list.add(data[pos] as T)
            remaining--
            pos = (pos + 1) % capacity
        }
        return list
    }
}
