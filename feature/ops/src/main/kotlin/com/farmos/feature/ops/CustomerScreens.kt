package com.farmos.feature.ops

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farmos.core.design.AnimalFarmEmptyState
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmPermissionExplanation
import com.farmos.core.design.FarmVisualClass

/** A customer in the farm's register (FOS-SALES-002/003). */
data class CustomerView(val customerId: String, val name: String, val phone: String?, val active: Boolean)

private fun customerNameValid(name: String) = name.isNotBlank() && name.trim().length <= 120

private fun phoneValid(phone: String) = phone.isBlank() || (phone.trim().length <= 32 && phone.trim().all { it.isDigit() || it in "+-() " })

/**
 * FOS-SALES-002 Customer list and FOS-SALES-003 Customer detail: the people and businesses the farm sells
 * to. Farm staff add and update customers; nobody deletes one, so recorded sales keep who they were for.
 */
@Composable
fun CustomerScreens(
    customers: List<CustomerView>,
    canManage: Boolean,
    busy: Boolean,
    error: String?,
    onAdd: (name: String, phone: String?) -> Unit,
    onUpdate: (customerId: String, name: String, phone: String) -> Unit,
    onSetActive: (customerId: String, active: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = customers.firstOrNull { it.customerId == selectedId }
    if (selected != null) {
        CustomerDetail(selected, canManage, busy, error, onUpdate, onSetActive) { selectedId = null }
        return
    }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    FarmOperationalPage("FOS-SALES-002", "Customers", "People and businesses this farm sells to.", FarmVisualClass.I3, onBack, backLabel = "Sales") {
        if (canManage) {
            FarmOperationalSection("Add a customer") {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("customer-add-name"))
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone (optional)") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("customer-add-phone"))
                Button(
                    onClick = {
                        onAdd(name.trim(), phone.trim().ifBlank { null })
                        name = ""
                        phone = ""
                    },
                    enabled = !busy && customerNameValid(name) && phoneValid(phone),
                    modifier = Modifier.fillMaxWidth().testTag("customer-add"),
                ) { Text("Add customer") }
            }
        } else {
            FarmPermissionExplanation("Customers are kept by farm staff", "Your role can see the customers but not change them.")
        }
        if (customers.isEmpty()) {
            AnimalFarmEmptyState("No customers recorded on this farm.")
        } else {
            FarmOperationalSection("Customers · ${customers.count { it.active }} active") {
                customers.forEachIndexed { index, customer ->
                    if (index > 0) HorizontalDivider()
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = AnimalFarmTheme.minimumTouchDp.dp)
                            .clickable(role = Role.Button) { selectedId = customer.customerId }
                            .padding(vertical = 6.dp).testTag("customer:${customer.customerId}"),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(customer.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(listOfNotNull(if (customer.active) "Active" else "Inactive", customer.phone).joinToString(" · "), color = AnimalFarmTheme.colors.mutedInk)
                    }
                }
            }
        }
        error?.let { Text(it, color = AnimalFarmTheme.colors.critical) }
    }
}

@Composable
private fun CustomerDetail(
    customer: CustomerView,
    canManage: Boolean,
    busy: Boolean,
    error: String?,
    onUpdate: (String, String, String) -> Unit,
    onSetActive: (String, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var name by remember(customer.customerId, customer.name) { mutableStateOf(customer.name) }
    var phone by remember(customer.customerId, customer.phone) { mutableStateOf(customer.phone.orEmpty()) }
    FarmOperationalPage("FOS-SALES-003", customer.name, if (customer.active) "Active customer" else "Inactive customer", FarmVisualClass.I3, onBack, backLabel = "Customers") {
        FarmOperationalSection("Contact") {
            Text(customer.phone?.let { "Phone $it" } ?: "No phone recorded.")
        }
        if (canManage) {
            FarmOperationalSection("Details") {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("customer-edit-name"))
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone (optional)") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("customer-edit-phone"))
                TextButton(
                    onClick = { onUpdate(customer.customerId, name.trim(), phone.trim()) },
                    enabled = !busy && customerNameValid(name) && phoneValid(phone) && (name.trim() != customer.name || phone.trim() != customer.phone.orEmpty()),
                    modifier = Modifier.testTag("customer-save"),
                ) { Text("Save changes") }
            }
            FarmOperationalSection("Status") {
                Text(if (customer.active) "Inactive customers keep their sales but are not offered for new ones." else "Reactivating offers this customer for new sales again.")
                TextButton(onClick = { onSetActive(customer.customerId, !customer.active) }, enabled = !busy, modifier = Modifier.testTag("customer-set-active")) {
                    Text(if (customer.active) "Make inactive" else "Make active")
                }
            }
        }
        error?.let { Text(it, color = AnimalFarmTheme.colors.critical) }
    }
}
