package com.alertnotes.domain.model

/**
 * How the app runs. Room is the source of truth in both modes — [ONLINE]
 * only adds cloud capabilities on top; it never moves data off-device
 * without an explicit user action.
 */
enum class AppMode {
    /** Everything stays on this device. No account, no network. */
    OFFLINE,

    /** Signed in to a Firebase account; cloud features are available. */
    ONLINE,
}
