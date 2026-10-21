package com.kowloondairy.navprint.print

import org.junit.Assert.assertEquals
import org.junit.Test

class PrintProblemTest {

    @Test
    fun `unreachable printer message is in written Chinese`() {
        assertEquals(
            "無法連接打印機，請檢查打印機是否已開啟，以及手機是否已連接公司 wifi",
            PrintProblem.UNREACHABLE.message,
        )
    }
}
