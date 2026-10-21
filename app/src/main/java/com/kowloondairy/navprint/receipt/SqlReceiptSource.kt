package com.kowloondairy.navprint.receipt

import android.util.Log
import com.kowloondairy.navprint.landing.DeliveryNote
import com.kowloondairy.navprint.landing.SqlConnectionSettings
import com.kowloondairy.navprint.landing.SqlDeliveryNoteSource.Companion.locationCodesFor
import com.kowloondairy.navprint.landing.openReadOnly
import com.kowloondairy.navprint.landing.startTimestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal

/** Reads one 送貨單's lines and 訂單 headers from NAV. Read-only. */
class SqlReceiptSource(private val settings: SqlConnectionSettings) : ReceiptSource {

    override suspend fun load(note: DeliveryNote): Receipt =
        withContext(Dispatchers.IO) {
            try {
                buildReceipt(note, query(note))
            } catch (e: Exception) {
                Log.e(TAG, "Could not read 收據 for 送貨單 ${note.number}", e)
                throw e
            }
        }

    private fun query(note: DeliveryNote): List<ReceiptRow> {
        // Same date and 類別 filter as the landing list, so the 收據 has exactly the lines that 送貨單 stood for.
        val locations = locationCodesFor(note.category)
        val placeholders = locations.joinToString { "?" }
        val sql = """
            SELECT l.[Document No_], l.[Location Code], l.[Description], l.[Description 2], l.[Quantity],
                   h.[Ship-to Name], h.[Ship-to Contact], h.[MagTelephone],
                   h.[Ship-to Address], h.[Ship-to Address 2],
                   h.[Bill-to Address], h.[Bill-to Address 2], h.[Bill-to City],
                   h.[MagSub_district], i.[Short Chinese Description]
            FROM [dbo].[Home Delivery${'$'}Sales Line] l
            LEFT JOIN [dbo].[Home Delivery${'$'}Sales Header] h ON h.[No_] = l.[Document No_]
            LEFT JOIN [dbo].[Home Delivery${'$'}Item] i ON i.[No_] = l.[No_]
            WHERE l.[Delivery ID] = ?
              AND l.[Requested Delivery Date] >= ? AND l.[Requested Delivery Date] < ?
              AND l.[Location Code] IN ($placeholders)
            ORDER BY l.[Document No_], l.[Line No_]
        """.trimIndent()

        settings.openReadOnly().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, note.number)
                statement.setTimestamp(2, note.deliveryDate.startTimestamp())
                statement.setTimestamp(3, note.deliveryDate.plusDays(1).startTimestamp())
                locations.forEachIndexed { i, code -> statement.setString(4 + i, code) }

                val rows = mutableListOf<ReceiptRow>()
                statement.executeQuery().use { r ->
                    while (r.next()) {
                        rows += ReceiptRow(
                            orderNumber = r.getString(1).orEmpty().trim(),
                            locationCode = r.getString(2).orEmpty(),
                            englishName = r.getString(3).orEmpty(),
                            chineseName = r.getString(4).orEmpty(),
                            shortChineseName = r.getString(15).orEmpty(),
                            quantity = r.getBigDecimal(5) ?: BigDecimal.ZERO,
                            name = r.getString(6).orEmpty(),
                            contact = r.getString(7).orEmpty(),
                            phone = r.getString(8).orEmpty(),
                            shipToAddress = r.getString(9).orEmpty(),
                            shipToAddress2 = r.getString(10).orEmpty(),
                            billToAddress = r.getString(11).orEmpty(),
                            billToAddress2 = r.getString(12).orEmpty(),
                            billToCity = r.getString(13).orEmpty(),
                            message = r.getString(14),
                        )
                    }
                }
                check(rows.isNotEmpty()) { "送貨單 ${note.number} has no lines" }
                return rows
            }
        }
    }

    private companion object {
        const val TAG = "SqlReceiptSource"
    }
}
