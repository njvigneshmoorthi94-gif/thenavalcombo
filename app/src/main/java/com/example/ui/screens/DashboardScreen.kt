package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AddShoppingCart
import androidx.compose.material.icons.rounded.AssignmentReturn
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PendingActions
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.DateRangeFilter
import com.example.data.model.ShopOrder
import com.example.data.model.StockItem
import com.example.data.model.formatInr
import com.example.data.model.formatIstDateTime
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.GeminiAssistantBar
import com.example.ui.components.KpiCard
import com.example.ui.components.SalesBarChart
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusBadge
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavyCard
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusCyan
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.StatusRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.DashboardMetrics

@Composable
fun DashboardScreen(
    metrics: DashboardMetrics,
    selectedFilter: DateRangeFilter,
    onFilterChange: (DateRangeFilter) -> Unit,
    recentOrders: List<ShopOrder>,
    watchlistStocks: List<StockItem>,
    isSubmitting: Boolean,
    isGeminiProcessing: Boolean,
    onRunGeminiCommand: (String, () -> Unit) -> Unit,
    onSendWeeklyReportNow: () -> Unit,
    onQuickNewOrder: () -> Unit,
    onQuickAddStock: () -> Unit,
    onQuickCollectPayment: () -> Unit,
    onNavigateToOrders: () -> Unit,
    onNavigateToStock: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Gemini Assistant Bar
        item {
            GeminiAssistantBar(
                isProcessing = isGeminiProcessing || isSubmitting,
                onRunCommand = onRunGeminiCommand
            )
        }

        // Date Range Filter Tabs
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DateRangeFilter.entries.forEach { filter ->
                    val label = when (filter) {
                        DateRangeFilter.TODAY -> "Today Sales"
                        DateRangeFilter.WEEKLY -> "Weekly Sales"
                        DateRangeFilter.MONTHLY -> "Monthly Sales"
                    }
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { onFilterChange(filter) },
                        label = { Text(label) },
                        modifier = Modifier.testTag("dashboard_tab_${filter.name.lowercase()}"),
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

        // Quick Actions Row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onQuickNewOrder,
                    modifier = Modifier.testTag("quick_new_order_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
                ) {
                    Icon(Icons.Rounded.AddShoppingCart, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("New Order")
                }
                Button(
                    onClick = onSendWeeklyReportNow,
                    enabled = !isSubmitting,
                    modifier = Modifier.testTag("dashboard_send_weekly_report_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = StatusEmerald)
                ) {
                    Icon(Icons.Rounded.Email, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Send Weekly Report Now")
                }
                Button(
                    onClick = onQuickCollectPayment,
                    modifier = Modifier.testTag("quick_collect_payment_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                ) {
                    Icon(Icons.Rounded.Payments, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Collect Payment")
                }
                OutlinedButton(
                    onClick = onQuickAddStock,
                    modifier = Modifier.testTag("quick_add_stock_button"),
                    border = BorderStroke(1.dp, NavyBorder)
                ) {
                    Icon(Icons.Rounded.Inventory2, contentDescription = null, tint = AccentPurple)
                    Spacer(Modifier.width(6.dp))
                    Text("Add Stock", color = TextPrimary)
                }
            }
        }

        // Summary KPI Grid (2-column pairs)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiCard(
                        title = "Total Sales",
                        value = formatInr(metrics.totalSales),
                        subtitle = "${metrics.totalOrders} orders",
                        icon = Icons.Rounded.CurrencyRupee,
                        accentColor = AccentPurple,
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        title = "Collected Payments",
                        value = formatInr(metrics.collectedPayments),
                        subtitle = "Pending: ${formatInr(metrics.pendingBalance)}",
                        icon = Icons.Rounded.AccountBalanceWallet,
                        accentColor = StatusEmerald,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiCard(
                        title = "Completed Orders",
                        value = metrics.completedOrders.toString(),
                        icon = Icons.Rounded.CheckCircle,
                        accentColor = AccentBlue,
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        title = "Unpaid Orders",
                        value = metrics.unpaidOrders.toString(),
                        icon = Icons.Rounded.PendingActions,
                        accentColor = StatusAmber,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiCard(
                        title = "Returned Orders",
                        value = metrics.returnedOrders.toString(),
                        subtitle = formatInr(metrics.returnAmount),
                        icon = Icons.Rounded.AssignmentReturn,
                        accentColor = StatusCyan,
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        title = "Low Stock",
                        value = metrics.lowStockCount.toString(),
                        subtitle = "Alert threshold reached",
                        icon = Icons.Rounded.WarningAmber,
                        accentColor = StatusAmber,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToStock
                    )
                }
                KpiCard(
                    title = "Empty Stock",
                    value = "${metrics.emptyStockCount} items out of stock",
                    subtitle = "Total Stock Valuation: ${formatInr(metrics.stockValuation)}",
                    icon = Icons.Rounded.ErrorOutline,
                    accentColor = StatusRed,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onNavigateToStock
                )
            }
        }

        // Sales Chart
        item {
            SalesBarChart(series = metrics.dailySeries)
        }

        // Payment Summary Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = NavyCard),
                border = BorderStroke(1.dp, NavyBorder.copy(alpha = 0.7f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Payment Summary (${selectedFilter.label})", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Collected", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            Text(
                                formatInr(metrics.collectedPayments),
                                style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMono),
                                color = StatusEmerald
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Pending Balance", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            Text(
                                formatInr(metrics.pendingBalance),
                                style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMono),
                                color = StatusAmber
                            )
                        }
                    }
                }
            }
        }

        // Low & Empty Stock Watchlist
        item {
            SectionHeader(
                title = "Low & Empty Stock Watchlist",
                actionLabel = "View Stock",
                onActionClick = onNavigateToStock
            )
        }

        if (watchlistStocks.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.Inventory2,
                    title = "All Stock Healthy",
                    description = "No items are currently below their low-stock alert threshold."
                )
            }
        } else {
            items(watchlistStocks.take(5), key = { it.id }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyCard),
                    border = BorderStroke(1.dp, NavyBorder.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${item.brand} • ${item.partName}", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text(
                                "Qty: ${item.quantity} | Restock +${item.restockNeeded} suggested",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        StatusBadge(label = item.computedStatus.label)
                    }
                }
            }
        }

        // Recent Orders
        item {
            SectionHeader(
                title = "Recent Orders",
                actionLabel = "All Orders",
                onActionClick = onNavigateToOrders
            )
        }

        if (recentOrders.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.AddShoppingCart,
                    title = "No Orders Yet",
                    description = "Tap New Order above to record a customer sale and deduct stock automatically."
                )
            }
        } else {
            items(recentOrders.take(5), key = { it.id }) { order ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyCard),
                    border = BorderStroke(1.dp, NavyBorder.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${order.customerName} (${order.orderNumber})", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("${order.brandSummary} ${order.partSummary} × ${order.totalQuantity}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            Text(formatIstDateTime(order.createdAt), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                formatInr(order.finalAmount),
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            StatusBadge(label = order.orderStatus)
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "@2026 thennaval powered by suwa",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 72.dp)
            )
        }
    }
}
