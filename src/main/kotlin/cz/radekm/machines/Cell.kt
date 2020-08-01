package cz.radekm.machines

/**
 * Cell which can be either empty or hold single value.
 */
class Cell<A> {
    var value: A? = null
    // `defined` is necessary - since `A` can be nullable `a == null`
    // may mean that value is defined.
    var defined = false

    fun isFull(): Boolean = defined
    fun isEmpty(): Boolean = !defined
    fun clear() {
        defined = false
    }
    fun put(a: A) {
        check(!defined) { "Full cell" }
        defined = true
        value = a
    }
    fun take(): A {
        check(defined) { "Empty cell" }
        defined = false
        // We cannot use `!!` ie. `value!!` because `A` can be nullable
        // and `null` can be proper value of defined cell.
        @Suppress("UNCHECKED_CAST")
        return value as A
    }
}
