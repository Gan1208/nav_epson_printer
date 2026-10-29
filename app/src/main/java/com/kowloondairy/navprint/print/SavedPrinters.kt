package com.kowloondairy.navprint.print

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * A 已儲存打印機: one that has printed a test page from this phone. Blank [name] means none given.
 */
data class SavedPrinter(val address: String, val model: String?, val name: String = "") {
    /** What staff call it: its name, or its IP when it has none. */
    val label: String
        get() = name.ifBlank { address }
}

/** Keeps no more than this many, dropping the oldest. */
const val MAX_SAVED_PRINTERS = 10

/**
 * [printer] moved to the top after a test print went through.
 * One already saved at that IP keeps its name; the newest model wins.
 */
fun List<SavedPrinter>.withSuccess(printer: SavedPrinter): List<SavedPrinter> {
    val old = firstOrNull { it.address == printer.address }
    val kept = printer.copy(
        name = old?.name ?: printer.name,
        model = printer.model ?: old?.model,
    )
    return (listOf(kept) + filter { it.address != printer.address }).take(MAX_SAVED_PRINTERS)
}

/** Newest first. */
interface SavedPrinterStore {
    var printers: List<SavedPrinter>
}

class SharedPrefsSavedPrinterStore(context: Context) : SavedPrinterStore {
    private val prefs = context.getSharedPreferences("printer", Context.MODE_PRIVATE)

    override var printers: List<SavedPrinter>
        get() {
            val json = JSONArray(prefs.getString(KEY_SAVED, "[]"))
            return List(json.length()) { i ->
                val it = json.getJSONObject(i)
                SavedPrinter(
                    address = it.getString("address"),
                    model = it.optString("model").ifEmpty { null },
                    name = it.optString("name"),
                )
            }
        }
        set(value) {
            val json = JSONArray()
            value.forEach {
                json.put(
                    JSONObject()
                        .put("address", it.address)
                        .put("model", it.model.orEmpty())
                        .put("name", it.name),
                )
            }
            prefs.edit().putString(KEY_SAVED, json.toString()).apply()
        }

    private companion object {
        const val KEY_SAVED = "saved"
    }
}
