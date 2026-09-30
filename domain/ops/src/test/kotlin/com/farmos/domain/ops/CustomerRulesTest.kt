package com.farmos.domain.ops

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CustomerRulesTest {
    @Test
    fun namesAndPhonesAreChecked() {
        assertNull(CustomerRules.create(CreateFarmCustomer("c1", "Moyo Butchery", "+263 77 123 4567")))
        assertNull(CustomerRules.create(CreateFarmCustomer("c1", "Moyo Butchery")))
        assertEquals("Enter the customer's name", CustomerRules.create(CreateFarmCustomer("c1", " ")))
        assertEquals("A phone number has digits, spaces, +, - and brackets only", CustomerRules.create(CreateFarmCustomer("c1", "Moyo", "call me")))
        assertEquals("Nothing to change", CustomerRules.update(UpdateFarmCustomer("c1")))
        assertNull(CustomerRules.update(UpdateFarmCustomer("c1", phone = "")))
        assertNull(CustomerRules.update(UpdateFarmCustomer("c1", active = false)))
    }
}
