package com.kowloondairy.navprint.print

import android.content.Context
import com.kowloondairy.navprint.landing.Category
import org.json.JSONArray
import org.json.JSONObject

/**
 * A 已儲存打印機: one that has printed a test page from this phone. Blank [name] means none given.
 * [category] is the 類別 this phone looks up while it is in use; null means 全部.
 */
data class SavedPrinter(val address: String, val model: String?, val name: String = "", val category: Category? = null) {
    /** What staff call it: its name, or its IP when it has none. */
    val label: String
        get() = name.ifBlank { address }
}

/** Keeps no more than this many, dropping the oldest. */
const val MAX_SAVED_PRINTERS = 10

/**
 * [printer] moved to the top after a test print went through.
 * One already saved at that IP keeps its name and 類別; the newest model wins.
 */
fun List<SavedPrinter>.withSuccess(printer: SavedPrinter): List<SavedPrinter> {
    val old = firstOrNull { it.address == printer.address }
    val kept = printer.copy(
        name = old?.name ?: printer.name,
        model = printer.model ?: old?.model,
        category = old?.category ?: printer.category,
    )
    return (listOf(kept) + filter { it.address != printer.address }).take(MAX_SAVED_PRINTERS)
}

/** e.g. 「倉庫」, for the printer at [address]: its label, or the IP when it is not saved. */
fun printerName(address: String, saved: List<SavedPrinter>): String = when {
    address.isBlank() -> "未設定打印機"
    else -> saved.firstOrNull { it.address == address }?.label ?: address
}

/** e.g. 「打印機：倉庫」, for the printer at [address]. */
fun printerInUse(address: String, saved: List<SavedPrinter>): String = when {
    address.isBlank() -> "未設定打印機"
    else -> "打印機：${printerName(address, saved)}"
}

/** The 類別 of the printer at [address]; 全部 (null) when it is not a 已儲存打印機. */
fun categoryInUse(address: String, saved: List<SavedPrinter>): Category? =
    saved.firstOrNull { it.address == address }?.category

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
                    category = Category.entries.firstOrNull { c -> c.name == it.optString("category") },
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
                        .put("name", it.name)
                        .put("category", it.category?.name.orEmpty()),
                )
            }
            prefs.edit().putString(KEY_SAVED, json.toString()).apply()
        }

    private companion object {
        const val KEY_SAVED = "saved"
    }
}
