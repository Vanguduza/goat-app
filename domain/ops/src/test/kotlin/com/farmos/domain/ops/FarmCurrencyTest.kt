package com.farmos.domain.ops

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FarmCurrencyTest {
    @Test
    fun theDefaultIsUsdAndRecordable() {
        assertEquals("USD", FarmCurrency.DEFAULT_CODE)
        assertTrue(FarmCurrency.isRecordable(FarmCurrency.DEFAULT_CODE))
    }

    @Test
    fun onlyUpperCaseIso4217CodesWithAMinorUnitAreRecordable() {
        listOf("USD", "ZAR", "KES", "JPY", "BHD", "EUR").forEach { assertTrue(FarmCurrency.isRecordable(it), it) }
        listOf("usd", "US", "USDX", "ABC", "", "XAU").forEach { assertFalse(FarmCurrency.isRecordable(it), it) }
        listOf("ZAL", "DEM", "ZWD", "USN", "CLF").forEach { assertFalse(FarmCurrency.isRecordable(it), "$it is withdrawn or a fund unit") }
        assertEquals(listOf("ZAR"), FarmCurrency.search("rand").map { it.currencyCode })
    }

    @Test
    fun minorDigitsFollowTheCurrencyNotAFixedTwo() {
        assertEquals(2, FarmCurrency.minorDigits("USD"))
        assertEquals(0, FarmCurrency.minorDigits("JPY"))
        assertEquals(3, FarmCurrency.minorDigits("BHD"))
        assertFailsWith<IllegalArgumentException> { FarmCurrency.minorDigits("ABC") }
    }

    @Test
    fun searchMatchesCodeOrNameAndCoversTheWholeList() {
        assertEquals(FarmCurrency.recordable, FarmCurrency.search(" "))
        assertTrue(FarmCurrency.recordable.size > 100)
        assertTrue(FarmCurrency.search("rand").any { it.currencyCode == "ZAR" })
        assertTrue(FarmCurrency.search("kes").any { it.currencyCode == "KES" })
        assertEquals(FarmCurrency.recordable.map { it.currencyCode }.sorted(), FarmCurrency.recordable.map { it.currencyCode })
    }

    @Test
    fun moneySalesAndPurchasesRejectACurrencyThatIsNotIso4217() {
        assertNull(OpsValidator.money(RecordMoney("m", "income", "sales", 100, "ZAR", 1)))
        assertEquals("Currency must be an ISO 4217 code", OpsValidator.money(RecordMoney("m", "income", "sales", 100, "ABC", 1)))
        assertEquals("Currency must be an ISO 4217 code", OpsValidator.sale(RecordSale("s", "live_goat", 1000, 100, "usd", 1)))
        assertEquals("Currency must be an ISO 4217 code", OpsValidator.purchase(RecordPurchase("p", "sup", "item", 1000, 100, "XYZ", 1)))
        assertNull(OpsValidator.purchase(RecordPurchase("p", "sup", "item", 1000, 100, "KES", 1)))
    }
}
