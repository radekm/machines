package cz.radekm.machines

/**
 * This `Option` allows `null` - this is difference from `java.util.Optional`.
 */
sealed class Option<out A> {
    abstract val isEmpty: Boolean
    abstract val isPresent: Boolean
    abstract fun get(): A
}
object None : Option<Nothing>() {
    override val isEmpty: Boolean = true
    override val isPresent: Boolean = false
    override fun get(): Nothing = error("Empty")

}
data class Some<out A>(private val a: A) : Option<A>() {
    override val isEmpty: Boolean = false
    override val isPresent: Boolean = true
    override fun get(): A = a
}
