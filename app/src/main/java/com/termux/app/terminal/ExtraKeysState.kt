package com.termux.app.terminal

/** Shared toggle state for the on-screen modifier keys of the terminal toolbar. */
class ExtraKeysState {
    @Volatile var control: Boolean = false
    @Volatile var alt: Boolean = false
    @Volatile var shift: Boolean = false
    @Volatile var fn: Boolean = false

    fun clearModifiers() {
        control = false
        alt = false
        shift = false
    }
}
