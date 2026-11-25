package com.example.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AssignmentReturn
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.PendingActions
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.data.model.DateRangeFilter
import com.example.data.model.PaymentRecord
import com.example.data.model.ReturnRecord
import com.example.data.model.ShopOrder
import com.example.data.model.StockItem
import com.example.data.model.formatInr
import com.example.ui.components.KpiCard
import com.example.ui.components.SalesBarChart
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavyCard
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusCyan
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.ShopViewModel
import java.io.File

@Composable
fun ReportsScreen(
    selectedFilter: DateRangeFilter,
    onFilterChange: (DateRangeFilter) -> Unit,
    stocks: List<StockItem>,
    orders: List<ShopOrder>,
    payments: List<PaymentRecord>,
    returns: List<ReturnRecord>,
    onBuildCsv: (DateRangeFilter) -> String,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val metrics = remember(stocks, orders, payments, returns, selectedFilter) {
        ShopViewModel.computeMetrics(stocks, orders, payments, returns, selectedFilter)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DateRangeFilter.entries.forEach { filter ->
                    val label = when (filter) {
                        DateRangeFilter.TODAY -> "Daily Report"
                        DateRangeFilter.WEEKLY -> "Weekly Report"
                        DateRangeFilter.MONTHLY -> "Monthly Report"
                    }
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { onFilterChange(filter) },
                        label = { Text(label) },
                        modifier = Modifier.testTag("report_tab_${filter.name.lowercase()}"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentPurple,
                            selectedLabelColor = TextPrimary,
                            containerColor = NavyCard,
                            labelColor = TextSecondary
                        )
                    )
                }
            }
        }

        item {
            Button(
                onClick = {
                    val csvContent = onBuildCsv(selectedFilter)
                    shareCsvReport(context, selectedFilter, csvContent, onShowMessage)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("export_csv_button"),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Export ${selectedFilter.label} Report (CSV)")
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiCard(
                        title = "Total Sales",
                        value = formatInr(metrics.totalSales),
                        icon = Icons.Rounded.CurrencyRupee,
                        accentColor = AccentPurple,
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        title = "Total Profit",
                        value = metrics.totalProfit?.let { formatInr(it) } ?: "Not Recorded",
                        subtitle = if (metrics.totalProfit == null) "Add purchase cost in Stock" else "Net after cost",
                        icon = Icons.Rounded.TrendingUp,
                        accentColor = StatusEmerald,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiCard(
                        title = "Total Orders",
                        value = metrics.totalOrders.toString(),
                        icon = Icons.Rounded.ReceiptLong,
                        accentColor = AccentBlue,
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        title = "Completed Orders",
                        value = metrics.completedOrders.toString(),
                        icon = Icons.Rounded.CheckCircle,
                        accentColor = StatusEmerald,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiCard(
                        title = "Unpaid Orders",
                        value = metrics.unpaidOrders.toString(),
                        icon = Icons.Rounded.PendingActions,
                        accentColor = StatusAmber,
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        title = "Return Amount",
                        value = formatInr(metrics.returnAmount),
                        subtitle = "${metrics.returnedOrders} orders",
                        icon = Icons.Rounded.AssignmentReturn,
                        accentColor = StatusCyan,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiCard(
                        title = "Payment Collection",
                        value = formatInr(metrics.collectedPayments),
                        icon = Icons.Rounded.AccountBalanceWallet,
                        accentColor = StatusEmerald,
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        title = "Pending Balance",
                        value = formatInr(metrics.pendingBalance),
                        icon = Icons.Rounded.PendingActions,
                        accentColor = StatusAmber,
                        modifier = Modifier.weight(1f)
                    )
                }
                KpiCard(
                    title = "Current Stock Valuation",
                    value = formatInr(metrics.stockValuation),
                    subtitle = "${stocks.size} SKUs • ${metrics.lowStockCount} low • ${metrics.emptyStockCount} empty",
                    icon = Icons.Rounded.Inventory2,
                    accentColor = AccentPurple,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            SalesBarChart(series = metrics.dailySeries)
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NavyCard),
                border = BorderStroke(1.dp, NavyBorder.copy(alpha = 0.7f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Profit & Cost Accuracy", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(
                        "Total Profit is computed strictly from orders whose spare parts include a recorded purchase cost, ensuring transparent accounting.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }
}

private fun shareCsvReport(
    context: Context,
    filter: DateRangeFilter,
    csvContent: String,
    onShowMessage: (String) -> Unit
) {
    try {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, "thennaval_combo_${filter.name.lowercase()}_report.csv")
        file.writeText(csvContent)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Thennaval Combo - ${filter.label} Report")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export CSV Report"))
    } catch (e: Exception) {
        onShowMessage(e.localizedMessage ?: "Failed to export CSV report.")
    }
}
