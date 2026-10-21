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
    NO_ADDRESS("未設定打印機 IP，請去設定"),
    UNREACHABLE("連唔到打印機，請檢查打印機有冇開，同手機係咪連住公司 wifi"),
    BUSY("打印機忙緊，請等一陣再試"),
    NO_PAPER("打印機冇紙"),
    COVER_OPEN("打印機蓋打開咗"),
    OTHER("打印失敗"),
}

class PrintFailure(val problem: PrintProblem, cause: Throwable? = null) :
    Exception(problem.message, cause)
