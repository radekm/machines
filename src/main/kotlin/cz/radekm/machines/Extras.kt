package cz.radekm.machines

/**
 * Extracts output until the machine stops or until it runs out of input.
 * Returns the number of inputs fed to the machine.
 * When calling the machine must be already started.
 *
 * After this function returns the state is `NEEDS_INPUT` or `STOPPED`.
 */
inline fun <I, O> Machine<I, O>.feedManyCb(input: List<I>, crossinline cb: (O) -> Unit): Int {
    val m = this
    var fed = 0

    check(m.state == NEEDS_INPUT || m.state == HAS_OUTPUT || m.state == STOPPED) { "feedManyCb: Invalid state" }

    while (true) {
        while (m.state == HAS_OUTPUT) {
            cb(m.take())
        }
        while (m.state == NEEDS_INPUT && fed < input.size) {
            m.feed(input[fed++])
        }
        if (m.state == NEEDS_INPUT || m.state == STOPPED) return fed
        check(m.state == HAS_OUTPUT) { "Internal error: Impossible" }
    }
}

/**
 * Extracts output until the machine stops.
 * Calls `stop` whenever the machine needs input.
 * When calling the machine must be already started.
 *
 * After this function returns the state is `STOPPED`.
 */
inline fun <O> Machine<*, O>.drainCb(crossinline cb: (O) -> Unit) {
    val m = this

    check(m.state == NEEDS_INPUT || m.state == HAS_OUTPUT || m.state == STOPPED) { "drainCb: Invalid state" }

    while (true) {
        when (m.state) {
            NEEDS_INPUT -> m.stop()
            HAS_OUTPUT -> cb(m.take())
            STOPPED -> return
            else -> error("Internal error: Unexpected state ${m.state}")
        }
    }
}
