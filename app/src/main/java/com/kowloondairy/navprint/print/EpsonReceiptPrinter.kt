package com.kowloondairy.navprint.print

import android.content.Context
import android.graphics.Bitmap
import com.epson.epos2.Epos2CallbackCode
import com.epson.epos2.Epos2Exception
import com.epson.epos2.printer.Printer
import com.epson.epos2.printer.PrinterStatusInfo
import com.kowloondairy.navprint.receipt.ReceiptPage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** How long to keep trying while another phone is using the printer. */
private const val BUSY_WAIT_MS = 10_000L
private const val BUSY_RETRY_MS = 1_000L

/**
 * How long to wait for the printer to answer at all. The SDK's connect ignores its own
 * timeout when nothing answers, and waited over two minutes.
 */
private const val CONNECT_TIMEOUT_MS = 5_000

/** The printer's raw print port, which the SDK uses for TCP. */
private const val PRINT_PORT = 9100

/** How long the printer gets to print one job. Three long pages fit easily. */
private const val SEND_TIMEOUT_MS = 60_000

/** The Epson TM-m30II on the company wifi, through the Epson ePOS SDK. */
class EpsonReceiptPrinter(
    private val context: Context,
    private val addresses: PrinterAddressStore,
) : ReceiptPrinter {

    override suspend fun print(pages: List<ReceiptPage>, render: suspend (ReceiptPage) -> Bitmap) {
        // Draw everything first, so the printer is held only while sending.
        val images = pages.map { render(it) }
        send {
            images.forEach { image ->
                addImage(
                    image, 0, 0, image.width, image.height,
                    Printer.COLOR_1, Printer.MODE_MONO, Printer.HALFTONE_THRESHOLD,
                    Printer.PARAM_DEFAULT.toDouble(), Printer.COMPRESS_AUTO,
                )
                addCut(Printer.CUT_FEED)
            }
        }
    }

    override suspend fun printTest() = send {
        addTextAlign(Printer.ALIGN_CENTER)
        addText("Nav Print\nTest print OK\n")
        addCut(Printer.CUT_FEED)
    }

    private suspend fun send(build: Printer.() -> Unit) = withContext(Dispatchers.IO) {
        val address = addresses.address.trim()
        if (address.isEmpty()) throw PrintFailure(PrintProblem.NO_ADDRESS)

        checkReachable(address)
        val giveUpAt = System.currentTimeMillis() + BUSY_WAIT_MS
        while (true) {
            try {
                sendOnce(address, build)
                return@withContext
            } catch (e: PrinterBusy) {
                if (System.currentTimeMillis() >= giveUpAt) throw PrintFailure(PrintProblem.BUSY, e)
                delay(BUSY_RETRY_MS)
            }
        }
    }

    private suspend fun sendOnce(address: String, build: Printer.() -> Unit) {
        val printer = try {
            Printer(Printer.TM_M30II, Printer.MODEL_ANK, context)
        } catch (e: Epos2Exception) {
            throw PrintFailure(PrintProblem.OTHER, e)
        } catch (e: LinkageError) {
            // The SDK's native library is only shipped for ARM phones and x86_64 emulators.
            throw PrintFailure(PrintProblem.OTHER, e)
        }
        val done = CompletableDeferred<Pair<Int, PrinterStatusInfo?>>()
        printer.setReceiveEventListener { _, code, status, _ -> done.complete(code to status) }
        try {
            printer.build()
            printer.connect("TCP:$address", CONNECT_TIMEOUT_MS)
            try {
                printer.sendData(SEND_TIMEOUT_MS)
                // The SDK reports a timeout itself; this only guards against it never calling back.
                val (code, status) = withTimeout(SEND_TIMEOUT_MS + 10_000L) { done.await() }
                checkResult(code, status)
            } finally {
                runCatching { printer.disconnect() }
            }
        } catch (e: Epos2Exception) {
            throw when (e.errorStatus) {
                Epos2Exception.ERR_IN_USE -> PrinterBusy()
                Epos2Exception.ERR_CONNECT,
                Epos2Exception.ERR_TIMEOUT,
                Epos2Exception.ERR_NOT_FOUND,
                Epos2Exception.ERR_DISCONNECT -> PrintFailure(PrintProblem.UNREACHABLE, e)
                else -> PrintFailure(PrintProblem.OTHER, e)
            }
        } finally {
            printer.clearCommandBuffer()
            printer.setReceiveEventListener(null)
        }
    }

    /** Fails fast when nothing answers at [address], instead of leaving the SDK to hang. */
    private fun checkReachable(address: String) {
        try {
            Socket().use { it.connect(InetSocketAddress(address, PRINT_PORT), CONNECT_TIMEOUT_MS) }
        } catch (e: SocketTimeoutException) {
            throw PrintFailure(PrintProblem.UNREACHABLE, e)
        } catch (e: UnknownHostException) {
            throw PrintFailure(PrintProblem.UNREACHABLE, e)
        } catch (e: IOException) {
            // Something answered, e.g. a printer busy with another phone; the SDK says what.
        }
    }

    private fun checkResult(code: Int, status: PrinterStatusInfo?) {
        if (code == Epos2CallbackCode.CODE_SUCCESS) return
        throw when {
            code == Epos2CallbackCode.CODE_ERR_IN_USE ||
                code == Epos2CallbackCode.CODE_ERR_DEVICE_BUSY -> PrinterBusy()
            code == Epos2CallbackCode.CODE_ERR_EMPTY ||
                status?.paper == Printer.PAPER_EMPTY -> PrintFailure(PrintProblem.NO_PAPER)
            code == Epos2CallbackCode.CODE_ERR_COVER_OPEN ||
                status?.coverOpen == Printer.TRUE -> PrintFailure(PrintProblem.COVER_OPEN)
            code == Epos2CallbackCode.CODE_ERR_TIMEOUT ||
                code == Epos2CallbackCode.CODE_ERR_NOT_FOUND ||
                code == Epos2CallbackCode.CODE_ERR_CONNECT ||
                code == Epos2CallbackCode.CODE_ERR_DISCONNECT -> PrintFailure(PrintProblem.UNREACHABLE)
            else -> PrintFailure(PrintProblem.OTHER)
        }
    }

    /** Another phone is printing; worth trying again shortly. */
    private class PrinterBusy : Exception()
}
