package com.farmos.feature.ops

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.farmos.core.design.AnimalFarmEmptyState
import com.farmos.core.design.AnimalFarmTheme
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmSearchSelector
import com.farmos.core.design.FarmSelectionAtoms
import com.farmos.core.design.FarmSelectorOption
import com.farmos.core.design.FarmSelectorSearch
import com.farmos.core.design.FarmVisualClass
import java.time.LocalDate

/** An animal that left through a sale exit and has no sale money recorded yet (D-022). */
data class SaleExitView(val exitId: String, val animalId: String, val animalLabel: String, val soldTo: String, val soldOn: LocalDate, val price: String?)

private val NO_SALE_CUSTOMER = FarmSelectorOption("no-customer", "No customer recorded")

/**
 * FOS-SALES-007 — Animal sale: the money for an animal sold through a sale exit. The exit recorded that the
 * animal left; this records the income, linked to the exit so it is recorded once.
 */
@Composable
fun AnimalSaleScreen(
    exits: List<SaleExitView>,
    currency: String?,
    customerSearch: FarmSelectorSearch?,
    busy: Boolean,
    error: String?,
    onRecord: (exit: SaleExitView, amount: String, day: LocalDate, customerId: String?) -> Unit,
    onBack: () -> Unit,
) {
    var chosenId by remember { mutableStateOf<String?>(null) }
    val chosen = exits.firstOrNull { it.exitId == chosenId }
    var amount by remember(chosen?.exitId) { mutableStateOf(chosen?.price.orEmpty()) }
    var day by remember(chosen?.exitId) { mutableStateOf((chosen?.soldOn ?: LocalDate.now()).toString()) }
    var customer by remember(chosen?.exitId) { mutableStateOf(NO_SALE_CUSTOMER) }
    FarmOperationalPage("FOS-SALES-007", "Animal sale", "The money for an animal sold through a sale exit.", FarmVisualClass.I3, onBack, backLabel = "Sales") {
        if (exits.isEmpty()) {
            AnimalFarmEmptyState("No sold animals are waiting for their sale money.")
        } else {
            FarmOperationalSection("Sold animals · ${exits.size} waiting") {
                exits.forEachIndexed { index, exit ->
                    if (index > 0) HorizontalDivider()
                    val selected = exit.exitId == chosenId
                    TextButton(
                        onClick = { chosenId = exit.exitId },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().semantics { this.selected = selected }.testTag("animal-sale-exit:${exit.exitId}"),
                    ) {
                        Text(listOfNotNull(exit.animalLabel, "sold to ${exit.soldTo} on ${exit.soldOn}", exit.price?.let { "$it ${currency.orEmpty()}".trim() }, if (selected) "selected" else null).joinToString(" · "))
                    }
                }
            }
        }
        if (chosen != null) {
            FarmOperationalSection("Sale money") {
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount${currency?.let { " ($it)" } ?: ""}") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("animal-sale-amount"))
                OutlinedTextField(day, { day = it }, label = { Text("Date") }, placeholder = { Text("YYYY-MM-DD") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("animal-sale-date"))
                if (customerSearch != null) {
                    FarmSearchSelector(FarmSelectionAtoms.CUSTOMER_SELECTOR, "Customer (optional)", customerSearch, customer, { customer = it }, "No customers match on this device", enabled = !busy, pinned = listOf(NO_SALE_CUSTOMER))
                }
                val date = runCatching { LocalDate.parse(day) }.getOrNull()
                Button(
                    onClick = { if (date != null) onRecord(chosen, amount.trim(), date, customer.id.takeUnless { it == NO_SALE_CUSTOMER.id }) },
                    enabled = !busy && currency != null && amount.isNotBlank() && date != null,
                    modifier = Modifier.fillMaxWidth().testTag("animal-sale-record"),
                ) { Text("Record sale money") }
                Text("This posts income once for this sale exit.", color = AnimalFarmTheme.colors.mutedInk)
            }
        }
        error?.let { Text(it, color = AnimalFarmTheme.colors.critical) }
    }
}
