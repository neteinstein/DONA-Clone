package com.neteinstein.donaclone.core.model

/** "Closed" at 0%, otherwise "Open (N%)" — including "Open (100%)" for fully open, so the label
 * always conveys how far open the shutter is; only 0% needs its own word. */
fun shutterStateLabel(percentage: Int): String =
    if (percentage <= 0) "Closed" else "Open ($percentage%)"
