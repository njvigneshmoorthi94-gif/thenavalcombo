package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PendingActions
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.CatalogConstants
import com.example.data.model.DateRangeFilter
import com.example.data.model.PaymentRecord
import com.example.data.model.ShopOrder
import com.example.data.model.formatInr
import com.example.data.model.formatIstDateTime
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.KpiCard
import com.example.ui.components.SearchableDropdownField
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusBadge
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.NavyBlack
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyCardElevated
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusCyan
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.StatusRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.ShopViewModel

@Composable
fun PaymentsScreen(
    payments: List<PaymentRecord>,
    orders: List<ShopOrder>,
    isSubmitting: Boolean,
    openCollectDialogInitially: Boolean = false,
    onConsumeCollectTrigger: () -> Unit = {},
    onCollectPayment: (
        orderId: String,
        amount: Double,
        paymentMethod: String,
        notes: String,
        onDone: () -> Unit
    ) -> Unit
) {
    var showCollectDialog by remember { mutableStateOf(false) }
    var preselectedOrder by remember { mutableStateOf<ShopOrder?>(null) }

    val totalCollected = remember(payments) { payments.sumOf { it.amount } }
    val pendingOrders = remember(orders) { orders.filter { it.balanceAmount > 0.01 && it.orderStatus != "Returned" } }

    LaunchedEffect(openCollectDialogInitially) {
        if (openCollectDialogInitially) {
            preselectedOrder = pendingOrders.firstOrNull()
            showCollectDialog = true
            onConsumeCollectTrigger()
        }
    }
    val totalPending = remember(pendingOrders) { pendingOrders.sumOf { it.balanceAmount } }
    val todayCollected = remember(payments) {
        ShopViewModel.filterByDate(payments, DateRangeFilter.TODAY) { it.paymentDate ?: it.createdAt }.sumOf { it.amount }
    }
    val weeklyCollected = remember(payments) {
        ShopViewModel.filterByDate(payments, DateRangeFilter.WEEKLY) { it.paymentDate ?: it.createdAt }.sumOf { it.amount }
    }
    val monthlyCollected = remember(payments) {
        ShopViewModel.filterByDate(payments, DateRangeFilter.MONTHLY) { it.paymentDate ?: it.createdAt }.sumOf { it.amount }
    }

    Scaffold(
        containerColor = NavyBlack,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    preselectedOrder = pendingOrders.firstOrNull()
                    showCollectDialog = true
                },
                containerColor = AccentPurple,
                contentColor = TextPrimary,
                modifier = Modifier.testTag("collect_payment_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Collect Payment")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        KpiCard(
                            title = "Total Collected",
                            value = formatInr(totalCollected),
                            icon = Icons.Rounded.AccountBalanceWallet,
                            accentColor = StatusEmerald,
                            modifier = Modifier.weight(1f)
                        )
                        KpiCard(
                            title = "Total Pending",
                            value = formatInr(totalPending),
                            subtitle = "${pendingOrders.size} orders due",
                            icon = Icons.Rounded.PendingActions,
                            accentColor = StatusAmber,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        KpiCard(
                            title = "Today",
                            value = formatInr(todayCollected),
                            icon = Icons.Rounded.Today,
                            accentColor = AccentBlue,
                            modifier = Modifier.weight(1f)
                        )
                        KpiCard(
                            title = "Weekly",
                            value = formatInr(weeklyCollected),
                            icon = Icons.Rounded.Payments,
                            accentColor = AccentPurple,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    KpiCard(
                        title = "Monthly Collection",
                        value = formatInr(monthlyCollected),
                        icon = Icons.Rounded.CalendarMonth,
                        accentColor = StatusCyan,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (pendingOrders.isNotEmpty()) {
                item {
                    SectionHeader(title = "Orders With Pending Balance")
                }
                items(pendingOrders, key = { "due_${it.id}" }) { dueOrder ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = NavyCard),
                        border = BorderStroke(1.dp, StatusAmber.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${dueOrder.customerName} (${dueOrder.orderNumber})", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text(
                                    "Total: ${formatInr(dueOrder.finalAmount)} • Paid: ${formatInr(dueOrder.paidAmount)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                                Text(
                                    "Balance: ${formatInr(dueOrder.balanceAmount)}",
                                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono),
                                    color = StatusAmber
                                )
                            }
                            Button(
                                onClick = {
                                    preselectedOrder = dueOrder
                                    showCollectDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                            ) {
                                Text("Collect")
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader(title = "Payment History")
            }

            if (payments.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Rounded.Payments,
                        title = "No Payments Recorded",
                        description = "Collect full or partial payments from orders using Cash, UPI, or Bank Transfer."
                    )
                }
            } else {
                items(payments, key = { it.id }) { pay ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = NavyCard),
                        border = BorderStroke(1.dp, NavyBorder.copy(alpha = 0.7f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("${pay.customerName} • ${pay.orderNumber}", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text(
                                    "${pay.paymentMethod} • Collected by ${pay.collectedBy}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                                Text(formatIstDateTime(pay.paymentDate ?: pay.createdAt), style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    formatInr(pay.amount),
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
                                    color = StatusEmerald
                                )
                                StatusBadge(label = "Paid")
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showCollectDialog) {
        CollectPaymentDialog(
            pendingOrders = pendingOrders,
            initialOrder = preselectedOrder,
            isSubmitting = isSubmitting,
            onDismiss = { showCollectDialog = false },
            onSubmit = { orderId, amount, method, notes ->
                onCollectPayment(orderId, amount, method, notes) {
                    showCollectDialog = false
                }
            }
        )
    }
}

@Composable
private fun CollectPaymentDialog(
    pendingOrders: List<ShopOrder>,
    initialOrder: ShopOrder?,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (orderId: String, amount: Double, method: String, notes: String) -> Unit
) {
    var selectedOrder by remember { mutableStateOf(initialOrder ?: pendingOrders.firstOrNull()) }
    var amountText by remember(selectedOrder?.id) {
        mutableStateOf(selectedOrder?.balanceAmount?.toString() ?: "")
    }
    var paymentMethod by remember { mutableStateOf("UPI") }
    var notes by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val orderOptions = remember(pendingOrders) {
        pendingOrders.map { "${it.orderNumber} • ${it.customerName} (Due ${formatInr(it.balanceAmount)})" }
    }
    val currentLabel = selectedOrder?.let {
        "${it.orderNumber} • ${it.customerName} (Due ${formatInr(it.balanceAmount)})"
    } ?: ""

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyCardElevated,
        title = { Text("Collect Payment", color = TextPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (pendingOrders.isEmpty()) {
                    Text("All active orders are fully settled!", color = StatusEmerald)
                } else {
                    SearchableDropdownField(
                        label = "Select Order",
                        selectedValue = currentLabel,
                        options = orderOptions,
                        onSelected = { label ->
                            val idx = orderOptions.indexOf(label)
                            if (idx in pendingOrders.indices) selectedOrder = pendingOrders[idx]
                        },
                        testTag = "payment_order_dropdown"
                    )
                    selectedOrder?.let { ord ->
                        Text(
                            "Total: ${formatInr(ord.finalAmount)} • Paid: ${formatInr(ord.paidAmount)} • Balance: ${formatInr(ord.balanceAmount)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Payment Amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("payment_amount_input")
                    )
                    SearchableDropdownField(
                        label = "Payment Method",
                        selectedValue = paymentMethod,
                        options = CatalogConstants.PAYMENT_METHODS,
                        onSelected = { paymentMethod = it },
                        testTag = "payment_method_dropdown"
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (errorMsg != null) {
                    Text(errorMsg.orEmpty(), color = StatusRed, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val ord = selectedOrder ?: return@Button
                    val amt = amountText.toDoubleOrNull()
                    if (amt == null || amt <= 0.0) {
                        errorMsg = "Enter valid amount greater than ₹0."
                        return@Button
                    }
                    if (amt > ord.balanceAmount + 0.01) {
                        errorMsg = "Amount cannot exceed balance ${formatInr(ord.balanceAmount)}."
                        return@Button
                    }
                    onSubmit(ord.id, amt, paymentMethod, notes)
                },
                enabled = pendingOrders.isNotEmpty() && !isSubmitting,
                modifier = Modifier.testTag("confirm_payment_button"),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
            ) {
                Text("Save Payment")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
