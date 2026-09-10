package com.farmos.feature.ops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.farmos.core.design.FarmOperationalPage
import com.farmos.core.design.FarmOperationalRows
import com.farmos.core.design.FarmOperationalSection
import com.farmos.core.design.FarmVisualClass
import java.time.LocalDate

private enum class FinancePage { DASHBOARD, TRANSACTIONS, INCOME, EXPENSE }

/** FOS-FIN-001/002/004/005 — exact-money operational reference family. */
@Composable
fun MoneyCaptureScreen(
    rows: List<String>,
    busy: Boolean,
    error: String?,
    onRecord: (kind: String, category: String, amount: String, day: String) -> Unit,
    onBack: () -> Unit,
) {
    var page by remember { mutableStateOf(FinancePage.DASHBOARD) }
    when (page) {
        FinancePage.DASHBOARD -> FinanceDashboard(rows, error, onOpen = { page = it }, onBack = onBack)
        FinancePage.TRANSACTIONS -> FinanceTransactions(rows, error) { page = FinancePage.DASHBOARD }
        FinancePage.INCOME -> FinanceRecord("income", busy, error, onRecord) { page = FinancePage.DASHBOARD }
        FinancePage.EXPENSE -> FinanceRecord("expense", busy, error, onRecord) { page = FinancePage.DASHBOARD }
    }
}

@Composable
private fun FinanceDashboard(
    rows: List<String>,
    error: String?,
    onOpen: (FinancePage) -> Unit,
    onBack: () -> Unit,
) {
    FarmOperationalPage(
        screenId = "FOS-FIN-001",
        title = "Finance",
        subtitle = "Income and expense records on this device.",
        visualClass = FarmVisualClass.I2,
        onBack = onBack,
    ) {
        FarmOperationalSection("Farm money") {
            Text("${rows.size} recent transaction(s)", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Values are persisted as integer minor units; this view never uses floating-point money.")
        }
        FarmOperationalSection("Quick actions") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = { onOpen(FinancePage.INCOME) }) { Text("Record income") }
                Button(onClick = { onOpen(FinancePage.EXPENSE) }) { Text("Record expense") }
            }
            TextButton(onClick = { onOpen(FinancePage.TRANSACTIONS) }) { Text("View transactions") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun FinanceTransactions(
    rows: List<String>,
    error: String?,
    onBack: () -> Unit,
) {
    FarmOperationalPage(
        screenId = "FOS-FIN-002",
        title = "Transactions",
        subtitle = "Recent farm income and expense records.",
        onBack = onBack,
    ) {
        FarmOperationalRows(rows, "No money records yet", "Income and expenses will appear here after local capture.")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun FinanceRecord(
    kind: String,
    busy: Boolean,
    error: String?,
    onRecord: (kind: String, category: String, amount: String, day: String) -> Unit,
    onBack: () -> Unit,
) {
    var category by remember { mutableStateOf(if (kind == "income") "sales" else "feed") }
    var amount by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toString()) }
    val income = kind == "income"
    FarmOperationalPage(
        screenId = if (income) "FOS-FIN-004" else "FOS-FIN-005",
        title = if (income) "Record income" else "Record expense",
        subtitle = if (income) "Capture money earned by the farm." else "Capture a farm operating cost.",
        onBack = onBack,
    ) {
        FarmOperationalSection("Details", "Enter a decimal amount such as 12.50. Storage converts it to exact minor units.") {
            OutlinedTextField(category, {
                category = it
            }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(amount, {
                amount = it
            }, label = { Text("Amount") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            OutlinedTextField(
                day,
                { day = it },
                label = { Text("Date") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                singleLine = true,
            )
            Button(
                onClick = { onRecord(kind, category, amount, day) },
                enabled = !busy && category.isNotBlank() && amount.isNotBlank() && runCatching { LocalDate.parse(day) }.isSuccess,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (income) "Save income" else "Save expense") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
