package cz.radekm.machines.experimental

import cz.radekm.machines.Machine
import cz.radekm.machines.PAUSED

interface CanResume {
    fun canResume(): Boolean
}

fun <Ctx : CanResume> runMany(machines: List<Machine<Ctx>>): Boolean {
    var everyMachinePaused = true
    var resumed = true
    // Until some machine is resumed.
    while (resumed) {
        resumed = false
        for (m in machines) {
            if (m.state == PAUSED && m.machineContext.canResume()) {
                resumed = true
                m.resume()
            }
            everyMachinePaused = everyMachinePaused && m.state == PAUSED
        }
    }
    return everyMachinePaused
}

fun <Ctx : CanResume> shutDownMany(machines: List<Machine<Ctx>>) {
    for (m in machines) {
        while (m.state == PAUSED) {
            if (m.machineContext.canResume()) {
                m.resume()
            } else {
                m.cancel()
            }
        }
    }
}
