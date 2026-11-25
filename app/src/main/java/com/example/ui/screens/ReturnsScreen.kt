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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AssignmentReturn
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
import com.example.data.model.ReturnRecord
import com.example.data.model.ShopOrder
import com.example.data.model.formatInr
import com.example.data.model.formatIstDateTime
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SearchableDropdownField
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusBadge
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.NavyBlack
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyCardElevated
import com.example.ui.theme.StatusCyan
import com.example.ui.theme.StatusRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ReturnsScreen(
    returns: List<ReturnRecord>,
    orders: List<ShopOrder>,
    isSubmitting: Boolean,
    onProcessReturn: (
        order: ShopOrder,
        returnQuantity: Long,
        reason: String,
        refundAmount: Double,
        returnStatus: String,
        onDone: () -> Unit
    ) -> Unit
) {
    var showReturnDialog by remember { mutableStateOf(false) }

    val eligibleOrders = remember(orders) { orders.filter { it.orderStatus != "Returned" } }

    Scaffold(
        containerColor = NavyBlack,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showReturnDialog = true },
                containerColor = AccentPurple,
                contentColor = TextPrimary,
                modifier = Modifier.testTag("process_return_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Process Return")
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
                SectionHeader(title = "Return & Refund History")
            }

            if (returns.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Rounded.AssignmentReturn,
                        title = "No Returns Logged",
                        description = "Tap + to find an order, log a return, restore stock, and update payment balances."
                    )
                }
            } else {
                items(returns, key = { it.id }) { ret ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = NavyCard),
                        border = BorderStroke(1.dp, NavyBorder.copy(alpha = 0.7f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${ret.customerName} (${ret.orderNumber})", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                    Text("${ret.product} × ${ret.quantity}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                }
                                StatusBadge(label = ret.status)
                            }
                            Text("Reason: ${ret.reason}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "By ${ret.processedBy} • ${formatIstDateTime(ret.returnDate ?: ret.createdAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                                Text(
                                    "Refund: ${formatInr(ret.refundAmount)}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
                                    color = StatusCyan
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showReturnDialog) {
        ReturnFormDialog(
            eligibleOrders = eligibleOrders,
            isSubmitting = isSubmitting,
            onDismiss = { showReturnDialog = false },
            onSubmit = { order, qty, reason, refund, status ->
                onProcessReturn(order, qty, reason, refund, status) {
                    showReturnDialog = false
                }
            }
        )
    }
}

@Composable
private fun ReturnFormDialog(
    eligibleOrders: List<ShopOrder>,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (
        order: ShopOrder,
        returnQuantity: Long,
        reason: String,
        refundAmount: Double,
        returnStatus: String
    ) -> Unit
) {
    var selectedOrder by remember { mutableStateOf(eligibleOrders.firstOrNull()) }
    var quantityText by remember { mutableStateOf("1") }
    var reason by remember { mutableStateOf("") }
    var refundText by remember(selectedOrder?.id) {
        mutableStateOf(selectedOrder?.paidAmount?.toString() ?: "0")
    }
    var returnStatus by remember { mutableStateOf("Processed") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val orderLabels = remember(eligibleOrders) {
        eligibleOrders.map { "${it.orderNumber} • ${it.customerName} (${it.brandSummary} ${it.partSummary})" }
    }
    val currentLabel = selectedOrder?.let {
        "${it.orderNumber} • ${it.customerName} (${it.brandSummary} ${it.partSummary})"
    } ?: ""

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyCardElevated,
        title = { Text("Process Product Return", color = TextPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (eligibleOrders.isEmpty()) {
                    Text("No eligible orders found for return.", color = TextSecondary)
                } else {
                    SearchableDropdownField(
                        label = "Find Order",
                        selectedValue = currentLabel,
                        options = orderLabels,
                        onSelected = { label ->
                            val idx = orderLabels.indexOf(label)
                            if (idx in eligibleOrders.indices) selectedOrder = eligibleOrders[idx]
                        },
                        testTag = "return_order_dropdown"
                    )
                    selectedOrder?.let { ord ->
                        Text(
                            "Customer: ${ord.customerName} (${ord.mobile})\nProduct: ${ord.brandSummary} ${ord.partSummary} • Max Qty: ${ord.totalQuantity}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = quantityText,
                            onValueChange = { quantityText = it },
                            label = { Text("Return Qty") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = refundText,
                            onValueChange = { refundText = it },
                            label = { Text("Refund (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Return Reason") },
                        singleLine = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("return_reason_input")
                    )
                    SearchableDropdownField(
                        label = "Return Status",
                        selectedValue = returnStatus,
                        options = CatalogConstants.RETURN_STATUSES,
                        onSelected = { returnStatus = it },
                        testTag = "return_status_dropdown"
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
                    val qty = quantityText.toLongOrNull() ?: 0L
                    val refund = refundText.toDoubleOrNull() ?: -1.0
                    if (qty !in 1L..ord.totalQuantity.coerceAtLeast(1L)) {
                        errorMsg = "Return quantity must be between 1 and ${ord.totalQuantity}."
                        return@Button
                    }
                    if (reason.isBlank()) {
                        errorMsg = "Enter a return reason."
                        return@Button
                    }
                    if (refund < 0.0) {
                        errorMsg = "Enter a valid refund amount."
                        return@Button
                    }
                    onSubmit(ord, qty, reason, refund, returnStatus)
                },
                enabled = eligibleOrders.isNotEmpty() && !isSubmitting,
                modifier = Modifier.testTag("confirm_return_button"),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
            ) {
                Text("Submit Return")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
