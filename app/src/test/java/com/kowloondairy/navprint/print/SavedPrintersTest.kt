package com.kowloondairy.navprint.print

import com.kowloondairy.navprint.landing.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedPrintersTest {

    @Test
    fun `no more than ten are kept, dropping the oldest`() {
        val saved = (1..10).map { SavedPrinter("192.168.1.$it", null) }

        val after = saved.withSuccess(SavedPrinter("192.168.1.99", null))

        assertEquals(10, after.size)
        assertEquals("192.168.1.99", after.first().address)
        assertEquals("192.168.1.9", after.last().address)
    }

    @Test
    fun `a model not found this time keeps the one found before`() {
        val saved = listOf(SavedPrinter("192.168.1.50", "TM-m30II", "倉庫"))

        assertEquals(saved, saved.withSuccess(SavedPrinter("192.168.1.50", null)))
    }

    @Test
    fun `the printer in use is shown by its name, else its IP`() {
        val saved = listOf(SavedPrinter("192.168.1.50", null, "倉庫"), SavedPrinter("192.168.1.51", null))

        assertEquals("打印機：倉庫", printerInUse("192.168.1.50", saved))
        assertEquals("打印機：192.168.1.51", printerInUse("192.168.1.51", saved))
        assertEquals("打印機：192.168.1.52", printerInUse("192.168.1.52", saved))
        assertEquals("未設定打印機", printerInUse("", saved))
    }

    @Test
    fun `the printer's name has no 打印機 in front`() {
        val saved = listOf(SavedPrinter("192.168.1.50", null, "倉庫"))

        assertEquals("倉庫", printerName("192.168.1.50", saved))
        assertEquals("192.168.1.52", printerName("192.168.1.52", saved))
        assertEquals("未設定打印機", printerName("", saved))
    }

    @Test
    fun `a printer test printed again keeps its category`() {
        val saved = listOf(SavedPrinter("192.168.1.50", "TM-m30II", "倉庫", Category.ICE_CREAM))

        assertEquals(saved, saved.withSuccess(SavedPrinter("192.168.1.50", "TM-m30II")))
    }

    @Test
    fun `the category in use is the in-use printer's, else 全部`() {
        val saved = listOf(SavedPrinter("192.168.1.50", null, "倉庫", Category.MILK), SavedPrinter("192.168.1.51", null))

        assertEquals(Category.MILK, categoryInUse("192.168.1.50", saved))
        assertNull(categoryInUse("192.168.1.51", saved))
        assertNull(categoryInUse("192.168.1.52", saved))
    }
}
