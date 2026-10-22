package com.kowloondairy.navprint.print

import android.content.Context

/** Where the printer in use is on the company wifi, e.g. `192.168.1.50`. Blank when not set. */
interface PrinterAddressStore {
    var address: String
}

class SharedPrefsPrinterAddressStore(context: Context) : PrinterAddressStore {
    private val prefs = context.getSharedPreferences("printer", Context.MODE_PRIVATE)

    override var address: String
        get() = prefs.getString(KEY_ADDRESS, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_ADDRESS, value).apply()

    private companion object {
        const val KEY_ADDRESS = "address"
    }
}
