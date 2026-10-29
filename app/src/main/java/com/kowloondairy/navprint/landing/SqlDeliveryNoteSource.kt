package com.kowloondairy.navprint.landing

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Timestamp
import java.time.LocalDate
import java.time.ZoneId

data class SqlConnectionSettings(
    val host: String,
    val port: Int,
    /** Blank when the server has no named instance. */
    val instance: String,
    val database: String,
    val user: String,
    val password: String,
) {
    val jdbcUrl: String
        get() = buildString {
            append("jdbc:jtds:sqlserver://$host:$port/$database")
            if (instance.isNotBlank()) append(";instance=$instance")
            append(";loginTimeout=$TIMEOUT_SECONDS;socketTimeout=$TIMEOUT_SECONDS")
        }

    private companion object {
        const val TIMEOUT_SECONDS = 10
    }
}

fun SqlConnectionSettings.open(): Connection {
    Class.forName("net.sourceforge.jtds.jdbc.Driver")
    return DriverManager.getConnection(jdbcUrl, user, password)
}

fun SqlConnectionSettings.openReadOnly(): Connection = open().apply { isReadOnly = true }

/** Android's Timestamp has no valueOf(LocalDateTime); midnight in the phone's zone is the same thing. */
fun LocalDate.startTimestamp() =
    Timestamp(atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())

/** Reads 送貨單 straight from the NAV database. Read-only. */
class SqlDeliveryNoteSource(private val settings: SqlConnectionSettings) : DeliveryNoteSource {

    override suspend fun find(deliveryDate: LocalDate, category: Category?): List<DeliveryNote> =
        withContext(Dispatchers.IO) {
            try {
                query(deliveryDate, category)
            } catch (e: Exception) {
                Log.e(TAG, "Could not read 送貨單 for $deliveryDate", e)
                throw e
            }
        }

    private fun query(deliveryDate: LocalDate, category: Category?): List<DeliveryNote> {
        val locations = locationCodesFor(category)
        val placeholders = locations.joinToString { "?" }
        // A 送貨單 has many lines; one row per (Delivery ID, location, 訂單) gives its 類別, 訂單 and customer.
        // Each line's 訂單 is [Document No_]; a 訂單 with no header or a NULL flag counts as not a 訂閱單.
        // Column 6 is the 送貨單's line count now, column 7 the count when it was printed (NULL if never):
        // it is 已打印 only when they match. A 送貨單 is one 類別, so the location filter keeps all its lines.
        val sql = """
            SELECT l.[Delivery ID], l.[Location Code], l.[Document No_],
                   MAX(CASE WHEN h.[MagIs_order_subscription] > 0 THEN 1 ELSE 0 END),
                   MAX(h.[Ship-to Name]),
                   SUM(COUNT(*)) OVER (PARTITION BY l.[Delivery ID]),
                   MAX(p.[Line Count])
            FROM [dbo].[Home Delivery${'$'}Sales Line] l
            LEFT JOIN [dbo].[Home Delivery${'$'}Sales Header] h ON h.[No_] = l.[Document No_]
            LEFT JOIN [dbo].[Home Delivery${'$'}Printed Record] p ON p.[Delivery ID] = l.[Delivery ID]
            WHERE l.[Requested Delivery Date] >= ? AND l.[Requested Delivery Date] < ?
              AND l.[Location Code] IN ($placeholders)
              AND l.[Delivery ID] <> ''
            GROUP BY l.[Delivery ID], l.[Location Code], l.[Document No_]
            ORDER BY l.[Delivery ID]
        """.trimIndent()

        settings.openReadOnly().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setTimestamp(1, deliveryDate.startTimestamp())
                statement.setTimestamp(2, deliveryDate.plusDays(1).startTimestamp())
                locations.forEachIndexed { i, code -> statement.setString(3 + i, code) }

                val notes = mutableListOf<DeliveryNote>()
                statement.executeQuery().use { rows ->
                    while (rows.next()) {
                        val number = rows.getString(1).trim()
                        val noteCategory = categoryOf(rows.getString(2).trim()) ?: continue
                        val printedLineCount = rows.getInt(7).takeUnless { rows.wasNull() }
                        notes += DeliveryNote(
                            number = number,
                            deliveryDate = deliveryDate,
                            category = noteCategory,
                            hasSubscriptionOrder = rows.getInt(4) > 0,
                            orderNumbers = listOf(rows.getString(3).orEmpty().trim()),
                            customerName = rows.getString(5).orEmpty().trim(),
                            printed = printedLineCount == rows.getInt(6),
                        )
                    }
                }
                return mergeByNumber(notes)
            }
        }
    }

    companion object {
        private const val TAG = "SqlDeliveryNoteSource"

        private val locationsByCategory = mapOf(
            Category.MILK to listOf("92", "93"),
            Category.ICE_CREAM to listOf("T1"),
        )

        /** [category] null means 全部: every location that belongs to some 類別. */
        fun locationCodesFor(category: Category?): List<String> =
            if (category == null) locationsByCategory.values.flatten()
            else locationsByCategory.getValue(category)

        fun categoryOf(locationCode: String): Category? =
            locationsByCategory.entries.firstOrNull { locationCode in it.value }?.key

        /**
         * Rows come one per 訂單, and a milk 送貨單 can have lines in both 92 and 93: keep one 送貨單
         * with all its 訂單, marked if any has a 訂閱單, named after its first 訂單 like the 收據 is.
         */
        fun mergeByNumber(notes: List<DeliveryNote>): List<DeliveryNote> =
            notes.groupBy { it.number }.values.map { same ->
                val byOrder = same.sortedBy { it.orderNumbers.firstOrNull().orEmpty() }
                byOrder.first().copy(
                    hasSubscriptionOrder = same.any { it.hasSubscriptionOrder },
                    orderNumbers = byOrder.flatMap { it.orderNumbers }.distinct(),
                )
            }
    }
}
