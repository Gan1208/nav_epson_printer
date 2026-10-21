package com.kowloondairy.navprint.print

/** Looks for a printer on the wifi, to tell which model sits at an address. */
interface PrinterFinder {
    /**
     * The model name of the printer at [address], e.g. `TM-m30II`,
     * or null when none answers within a few seconds.
     */
    suspend fun findModel(address: String): String?
}
