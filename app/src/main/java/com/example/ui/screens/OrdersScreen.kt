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
import androidx.compose.material.icons.rounded.AddShoppingCart
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.data.model.ShopOrder
import com.example.data.model.StockItem
import com.example.data.model.formatInr
import com.example.data.model.formatIstDateTime
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SearchableDropdownField
import com.example.ui.components.StatusBadge
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.NavyBlack
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyCardElevated
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.StatusRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun OrdersScreen(
    orders: List<ShopOrder>,
    stocks: List<StockItem>,
    actorName: String,
    isOwner: Boolean,
    isSubmitting: Boolean,
    openCreateDialogInitially: Boolean = false,
    onConsumeCreateTrigger: () -> Unit = {},
    onCreateOrder: (
        customerName: String,
        mobile: String,
        stockId: String,
        quantity: Long,
        unitPrice: Double?,
        discount: Double,
        paidAmount: Double,
        paymentMethod: String,
        labourName: String,
        onDone: () -> Unit
    ) -> Unit,
    onDeleteOrder: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("All") }
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(openCreateDialogInitially) {
        if (openCreateDialogInitially) {
            showCreateDialog = true
            onConsumeCreateTrigger()
        }
    }

    val filteredOrders = remember(orders, searchQuery, statusFilter) {
        orders.filter { order ->
            val matchesSearch = searchQuery.isBlank() ||
                order.customerName.contains(searchQuery, ignoreCase = true) ||
                order.mobile.contains(searchQuery, ignoreCase = true) ||
                order.orderNumber.contains(searchQuery, ignoreCase = true) ||
                order.brandSummary.contains(searchQuery, ignoreCase = true) ||
                order.partSummary.contains(searchQuery, ignoreCase = true)
            val matchesStatus = statusFilter == "All" || order.orderStatus == statusFilter
            matchesSearch && matchesStatus
        }
    }

    Scaffold(
        containerColor = NavyBlack,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = AccentPurple,
                contentColor = TextPrimary,
                modifier = Modifier.testTag("create_order_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Create New Order")
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
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search customer, mobile, order ID, or part...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("orders_search_input"),
                    shape = RoundedCornerShape(14.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val statuses = listOf("All") + CatalogConstants.ORDER_STATUSES
                    statuses.forEach { st ->
                        FilterChip(
                            selected = statusFilter == st,
                            onClick = { statusFilter = st },
                            label = { Text(st) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentPurple, selectedLabelColor = TextPrimary)
                        )
                    }
                }
            }

            if (filteredOrders.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Rounded.AddShoppingCart,
                        title = "No Orders Found",
                        description = "Create a new order to deduct stock atomically and track customer balances."
                    )
                }
            } else {
                items(filteredOrders, key = { it.id }) { order ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("order_card_${order.id}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = NavyCard),
                        border = BorderStroke(1.dp, NavyBorder.copy(alpha = 0.7f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${order.customerName} • ${order.mobile}", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                    Text(
                                        "${order.orderNumber} • ${order.brandSummary} ${order.partSummary} (×${order.totalQuantity})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }
                                StatusBadge(label = order.orderStatus)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Final", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                    Text(formatInr(order.finalAmount), style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono), color = TextPrimary)
                                }
                                Column {
                                    Text("Paid", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                    Text(formatInr(order.paidAmount), style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono), color = StatusEmerald)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Balance", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                    Text(
                                        formatInr(order.balanceAmount),
                                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono),
                                        color = if (order.balanceAmount > 0.0) StatusAmber else TextMuted
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Staff: ${order.labourName} • ${formatIstDateTime(order.createdAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                                if (isOwner) {
                                    IconButton(onClick = { onDeleteOrder(order.id) }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Delete order", tint = StatusRed)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showCreateDialog) {
        CreateOrderDialog(
            stocks = stocks,
            defaultLabourName = actorName,
            isSubmitting = isSubmitting,
            onDismiss = { showCreateDialog = false },
            onSubmit = { customerName, mobile, stockId, qty, unitPrice, discount, paidAmount, method, labourName ->
                onCreateOrder(customerName, mobile, stockId, qty, unitPrice, discount, paidAmount, method, labourName) {
                    showCreateDialog = false
                }
            }
        )
    }
}

@Composable
private fun CreateOrderDialog(
    stocks: List<StockItem>,
    defaultLabourName: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (
        customerName: String,
        mobile: String,
        stockId: String,
        quantity: Long,
        unitPrice: Double?,
        discount: Double,
        paidAmount: Double,
        paymentMethod: String,
        labourName: String
    ) -> Unit
) {
    val availableStocks = remember(stocks) { stocks.filter { it.quantity > 0L } }
    var customerName by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var selectedBrand by remember { mutableStateOf(availableStocks.firstOrNull()?.brand ?: "") }
    val matchingBrandStocks = remember(selectedBrand, availableStocks) {
        availableStocks.filter { it.brand == selectedBrand }
    }
    var selectedStockId by remember { mutableStateOf(availableStocks.firstOrNull()?.id ?: "") }
    val selectedStock = availableStocks.firstOrNull { it.id == selectedStockId } ?: matchingBrandStocks.firstOrNull()

    var quantityText by remember { mutableStateOf("1") }
    var unitPriceText by remember(selectedStock?.id) {
        mutableStateOf(selectedStock?.unitPrice?.toString() ?: "")
    }
    var discountText by remember { mutableStateOf("0") }
    var paidAmountText by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("Cash") }
    var labourName by remember { mutableStateOf(defaultLabourName) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val qty = (quantityText.toLongOrNull() ?: 0L).coerceAtLeast(0L)
    val price = (unitPriceText.toDoubleOrNull() ?: selectedStock?.unitPrice ?: 0.0).coerceAtLeast(0.0)
    val totalAmount = qty * price
    val discount = (discountText.toDoubleOrNull() ?: 0.0).coerceIn(0.0, totalAmount)
    val finalAmount = (totalAmount - discount).coerceAtLeast(0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyCardElevated,
        title = { Text("Create New Order", color = TextPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (availableStocks.isEmpty()) {
                    Text(
                        "No in-stock items available. Add stock inventory before creating an order.",
                        color = StatusAmber,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    OutlinedTextField(
                        value = customerName,
                        onValueChange = { customerName = it },
                        label = { Text("Customer Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("order_customer_input")
                    )
                    OutlinedTextField(
                        value = mobile,
                        onValueChange = { mobile = it },
                        label = { Text("Mobile Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("order_mobile_input")
                    )

                    val brandsInStock = remember(availableStocks) { availableStocks.map { it.brand }.distinct() }
                    SearchableDropdownField(
                        label = "Mobile Brand",
                        selectedValue = selectedBrand,
                        options = brandsInStock,
                        onSelected = { newBrand ->
                            selectedBrand = newBrand
                            val firstForBrand = availableStocks.firstOrNull { it.brand == newBrand }
                            selectedStockId = firstForBrand?.id ?: ""
                        },
                        testTag = "order_brand_dropdown"
                    )

                    val partLabels = remember(matchingBrandStocks) {
                        matchingBrandStocks.map { "${it.partName} (Avail: ${it.quantity} • ${formatInr(it.unitPrice)})" }
                    }
                    val currentPartLabel = selectedStock?.let {
                        "${it.partName} (Avail: ${it.quantity} • ${formatInr(it.unitPrice)})"
                    } ?: ""
                    SearchableDropdownField(
                        label = "Parts",
                        selectedValue = currentPartLabel,
                        options = partLabels,
                        onSelected = { chosen ->
                            val idx = partLabels.indexOf(chosen)
                            if (idx in matchingBrandStocks.indices) {
                                selectedStockId = matchingBrandStocks[idx].id
                            }
                        },
                        testTag = "order_part_dropdown"
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = quantityText,
                            onValueChange = { quantityText = it },
                            label = { Text("Qty (Max ${selectedStock?.quantity ?: 0})") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("order_quantity_input")
                        )
                        OutlinedTextField(
                            value = unitPriceText,
                            onValueChange = { unitPriceText = it },
                            label = { Text("Unit Price (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = discountText,
                            onValueChange = { discountText = it },
                            label = { Text("Discount (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = paidAmountText,
                            onValueChange = { paidAmountText = it },
                            label = { Text("Paid Now (₹)") },
                            placeholder = { Text("0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("order_paid_input")
                        )
                    }

                    SearchableDropdownField(
                        label = "Payment Method",
                        selectedValue = paymentMethod,
                        options = CatalogConstants.PAYMENT_METHODS,
                        onSelected = { paymentMethod = it },
                        testTag = "order_payment_method_dropdown"
                    )

                    OutlinedTextField(
                        value = labourName,
                        onValueChange = { labourName = it },
                        label = { Text("Labour / Staff Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Total: ${formatInr(totalAmount)}  •  Final: ${formatInr(finalAmount)}",
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
                        color = AccentBlue
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
                    val stock = selectedStock
                    if (stock == null) {
                        errorMsg = "Please select a valid spare part."
                        return@Button
                    }
                    if (customerName.isBlank()) {
                        errorMsg = "Enter customer name."
                        return@Button
                    }
                    if (mobile.trim().length < 5) {
                        errorMsg = "Enter valid mobile number."
                        return@Button
                    }
                    if (qty <= 0L) {
                        errorMsg = "Quantity must be at least 1."
                        return@Button
                    }
                    if (qty > stock.quantity) {
                        errorMsg = "Only ${stock.quantity} available in stock!"
                        return@Button
                    }
                    val paid = paidAmountText.toDoubleOrNull() ?: 0.0
                    onSubmit(
                        customerName,
                        mobile,
                        stock.id,
                        qty,
                        price,
                        discount,
                        paid,
                        paymentMethod,
                        labourName
                    )
                },
                enabled = availableStocks.isNotEmpty() && !isSubmitting,
                modifier = Modifier.testTag("confirm_order_button"),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
            ) {
                Text("Confirm Order")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
