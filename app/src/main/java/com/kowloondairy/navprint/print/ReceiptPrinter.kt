package com.kowloondairy.navprint.print

import android.graphics.Bitmap
import com.kowloondairy.navprint.receipt.ReceiptPage

/** Sends pages of a 收據 to the printer. */
interface ReceiptPrinter {
    /**
     * Prints [pages] in order, cutting after each, as one job.
     * [render] draws a page as a 576-dot-wide image.
     *
     * @throws PrintFailure when nothing, or only part, was printed.
     */
    suspend fun print(pages: List<ReceiptPage>, render: suspend (ReceiptPage) -> Bitmap)

    /** Prints a short slip to check the printer can be reached. */
    suspend fun printTest()
}

/** Why a print did not finish, in words for the person at the printer. */
enum class PrintProblem(val message: String) {
    NO_ADDRESS("未設定打印機 IP，請前往設定"),
    UNREACHABLE("無法連接打印機，請檢查打印機是否已開啟，以及手機是否已連接公司 wifi"),
    BUSY("打印機忙碌中，請稍後再試"),
    NO_PAPER("打印機缺紙"),
    COVER_OPEN("打印機蓋未關上"),
    OTHER("打印失敗"),
}

class PrintFailure(val problem: PrintProblem, cause: Throwable? = null) :
    Exception(problem.message, cause)
