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

class Pipeline<I, O> private constructor(val machines: Array<Machine<*, *>>) {

    companion object {
        /**
         * Input of the first machine must have type `I`,
         * output of the last machine must have type `O`.
         */
        fun <I, O> create(vararg machines: Machine<*, *>): Pipeline<I, O> {
            val copy = Array<Machine<*, *>>(machines.size) { machines[it] }
            return Pipeline(copy)
        }
    }

    /**
     * After calling this every machine has state
     * `NEEDS_INPUT` or `HAS_OUTPUT` or `STOPPED`.
     */
    fun start() = machines.forEach { it.start() }

    /**
     * Use when you want to feed the input into pipeline
     * and get output which pipeline produces.
     * This takes all available output from every machine in the pipeline.
     *
     * Feeds the first machine. Then output produced by the first machine
     * feeds into the second machine. And again output produced
     * by the second machine is fed to the third machine.
     * And so on until we get output from the last machine
     * which is output of the pipeline.
     *
     * After calling this every machine has state
     * `NEEDS_INPUT` or `STOPPED`.
     *
     * `buffer` and `buffer2` must be different lists.
     * `input` and `buffer` must also be different lists.
     * `input` and `buffer2` can be same.
     * When `i`-th machine stops before consuming its input
     * `cbLostInput(i, x)` is called for each input item `x`
     * which was not consumed.
     * `cbOutput` is called for each output item produced
     * by the pipeline.
     */
    @Suppress("UNCHECKED_CAST")
    inline fun feedCb(
            input: List<I>,
            buffer: MutableList<Any>,
            buffer2: MutableList<Any>,
            crossinline cbLostInput: (Int, Any) -> Unit,
            crossinline cbOutput: (O) -> Unit
    ) {
        var inputBuffer: List<Any> = input as List<Any>
        var outputBuffer: MutableList<Any> = buffer
        var nextInputBuffer = buffer
        var nextOutputBuffer = buffer2

        for (machineIndex in machines.indices) {
            outputBuffer.clear()

            val m = machines[machineIndex] as Machine<Any, Any>
            var fed = m.feedManyCb(inputBuffer) { outputBuffer.add(it) }

            // Input which wasn't fed is lost.
            while (fed < inputBuffer.size) {
                cbLostInput(machineIndex, inputBuffer[fed])
                fed++
            }

            inputBuffer = nextInputBuffer
            outputBuffer = nextOutputBuffer
            nextOutputBuffer = nextInputBuffer
            nextInputBuffer = outputBuffer
        }

        inputBuffer.forEach { cbOutput(it as O) }

        buffer.clear()
        buffer2.clear()
    }

    // Same as `feedCb` except that it doesn't use any input
    // and after feeding the machine it calls drain to ensure that
    // machine stops.
    /**
     * Use when you want to stop the pipeline without feeding it any input.
     * This takes all output from the pipeline and stops every machine in it.
     *
     * Drains the first machine. Then output produced by the first
     * machine feeds into the second machine and then drains it.
     * And again output produced by the second machine is fed to the third
     * machine and then the third machine is drained.
     *
     * After calling this every machine has state `STOPPED`.
     *
     * `buffer` and `buffer2` must be different lists.
     * When `i`-th machine stops before consuming its input
     * `cbLostInput(i, x)` is called for each input item `x`
     * which was not consumed.
     * `cbOutput` is called for each output item produced
     * by the pipeline.
     */
    @Suppress("UNCHECKED_CAST")
    inline fun drainCb(
            buffer: MutableList<Any>,
            buffer2: MutableList<Any>,
            crossinline cbLostInput: (Int, Any) -> Unit,
            crossinline cbOutput: (O) -> Unit
    ) {
        buffer.clear()

        var inputBuffer: List<Any> = buffer2
        var outputBuffer: MutableList<Any> = buffer
        var nextInputBuffer = buffer
        var nextOutputBuffer = buffer2

        for (machineIndex in machines.indices) {
            outputBuffer.clear()

            val m = machines[machineIndex] as Machine<Any, Any>
            var fed = m.feedManyCb(inputBuffer) { outputBuffer.add(it) }

            // Input which wasn't fed is lost.
            while (fed < inputBuffer.size) {
                cbLostInput(machineIndex, inputBuffer[fed])
                fed++
            }

            m.drainCb { outputBuffer.add(it) }

            inputBuffer = nextInputBuffer
            outputBuffer = nextOutputBuffer
            nextOutputBuffer = nextInputBuffer
            nextInputBuffer = outputBuffer
        }

        inputBuffer.forEach { cbOutput(it as O) }

        buffer.clear()
        buffer2.clear()
    }
}
