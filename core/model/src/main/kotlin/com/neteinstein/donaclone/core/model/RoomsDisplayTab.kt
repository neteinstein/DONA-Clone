package com.neteinstein.donaclone.core.model

/** Which tab a room-display preference (currently just "expanded by default") belongs to — the
 * Home and Sensors tabs each persist their own collapse-state default, so collapsing every room
 * on one tab doesn't change what the other tab shows the next time it's opened fresh. */
enum class RoomsDisplayTab {
    HOME,
    SENSORS,
}
