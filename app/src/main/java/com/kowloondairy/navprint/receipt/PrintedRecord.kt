package com.kowloondairy.navprint.receipt

import android.util.Log
import com.kowloondairy.navprint.landing.SqlConnectionSettings
import com.kowloondairy.navprint.landing.open
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Date

/** Where a 送貨單 is recorded as 已打印. */
interface PrintedRecord {
    /**
     * [receipt] has just come out of the printer, all three pages.
     * Never throws: the paper is already out, so a failure here only means the 未打印提醒 may ring for it.
     */
    suspend fun markPrinted(receipt: Receipt)
}

/**
 * Records through `usp_MarkOrderPrinted`, the one thing the app's SQL user may run besides reading,
 * once per (訂單, location). If it stops partway the 送貨單 stays 未打印, so the alert still rings.
 * See docs/adr/0001-mark-printed-through-stored-procedure.md and docs/adr/0002-printed-record-by-order.md.
 */
class SqlPrintedRecord(
    private val settings: SqlConnectionSettings,
    /** Who printed it, e.g. the printer's IP. */
    private val printedBy: () -> String,
) : PrintedRecord {

    override suspend fun markPrinted(receipt: Receipt) {
        val number = receipt.deliveryNote.number
        val deliveryDate = Date.valueOf(receipt.deliveryNote.deliveryDate.toString())
        withContext(Dispatchers.IO) {
            try {
                settings.open().use { connection ->
                    connection.prepareCall("{call [dbo].[usp_MarkOrderPrinted](?, ?, ?, ?, ?)}").use { call ->
                        receipt.orderLineCounts.forEach { order ->
                            call.setString(1, order.orderNumber)
                            call.setDate(2, deliveryDate)
                            call.setString(3, order.locationCode)
                            call.setInt(4, order.lineCount)
                            call.setString(5, printedBy().take(50))
                            call.execute()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Could not record 送貨單 $number as printed", e)
            }
        }
    }

    private companion object {
        const val TAG = "SqlPrintedRecord"
    }
}
