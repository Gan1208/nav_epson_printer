package com.kowloondairy.navprint.print

import android.content.Context
import com.epson.epos2.Epos2Exception
import com.epson.epos2.discovery.Discovery
import com.epson.epos2.discovery.DiscoveryListener
import com.epson.epos2.discovery.FilterOption
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** How long to look before giving up. Some wifi blocks the search altogether. */
private const val SEARCH_MS = 5_000L

/** The SDK refuses to stop while it is still calling back; how long to keep asking. */
private const val STOP_TRIES = 20
private const val STOP_RETRY_MS = 100L

/** The SDK runs only one search at a time, for the whole app. */
private val searching = Mutex()

/** Finds Epson printers on the company wifi with the Epson ePOS SDK's `Discovery`. */
class EpsonPrinterFinder(private val context: Context) : PrinterFinder {

    override suspend fun findModel(address: String): String? = searching.withLock {
        val found = CompletableDeferred<String>()
        // Called on the SDK's own thread.
        val listener = DiscoveryListener { info ->
            val model = info.deviceName
            if (info.ipAddress == address && !model.isNullOrBlank()) found.complete(model)
        }
        withContext(Dispatchers.Main) {
            try {
                startSearch(listener)
            } catch (e: Epos2Exception) {
                return@withContext null
            } catch (e: LinkageError) {
                // The SDK's native library is only shipped for ARM phones and x86_64 emulators.
                return@withContext null
            }
            try {
                withTimeoutOrNull(SEARCH_MS) { found.await() }
            } finally {
                withContext(NonCancellable) { stopSearch() }
            }
        }
    }

    private fun startSearch(listener: DiscoveryListener) {
        val filter = FilterOption().apply {
            portType = Discovery.PORTTYPE_TCP
            deviceType = Discovery.TYPE_PRINTER
        }
        try {
            Discovery.start(context, filter, listener)
        } catch (e: Epos2Exception) {
            if (e.errorStatus != Epos2Exception.ERR_ILLEGAL) throw e
            // A search was left running, e.g. after a stop that never went through.
            runCatching { Discovery.stop() }
            Discovery.start(context, filter, listener)
        }
    }

    private suspend fun stopSearch() {
        repeat(STOP_TRIES) {
            try {
                Discovery.stop()
                return
            } catch (e: Epos2Exception) {
                // Busy delivering a callback; anything else means it is already stopped.
                if (e.errorStatus != Epos2Exception.ERR_PROCESSING) return
                delay(STOP_RETRY_MS)
            }
        }
    }
}
