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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PointOfSale
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.CatalogConstants
import com.example.data.model.StockItem
import com.example.data.model.StockStatus
import com.example.data.model.formatInr
import com.example.data.model.formatIstDateTime
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.GeminiAssistantBar
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
import com.example.ui.theme.StatusRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun StockScreen(
    stocks: List<StockItem>,
    isOwner: Boolean,
    actorName: String,
    defaultLowLimit: Long,
    isSubmitting: Boolean,
    isGeminiProcessing: Boolean,
    openAddDialogInitially: Boolean = false,
    onConsumeAddTrigger: () -> Unit = {},
    onSaveStock: (
        stockId: String?,
        brand: String,
        partName: String,
        category: String,
        unitPrice: Double,
        purchaseCost: Double?,
        quantity: Long,
        lowStockLimit: Long,
        onDone: () -> Unit
    ) -> Unit,
    onAdjustQuantity: (StockItem, Long) -> Unit,
    onRecordQuickSale: (StockItem, Long, () -> Unit) -> Unit,
    onSetExactStock: (StockItem, Long, () -> Unit) -> Unit,
    onLoadSampleItems: () -> Unit,
    onRunGeminiCommand: (String, () -> Unit) -> Unit,
    onDeleteStock: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var statusFilter by remember { mutableStateOf<StockStatus?>(null) }
    var editingStock by remember { mutableStateOf<StockItem?>(null) }
    var showFormDialog by remember { mutableStateOf(false) }
    var deletingStock by remember { mutableStateOf<StockItem?>(null) }
    var customSaleTarget by remember { mutableStateOf<StockItem?>(null) }
    var exactStockTarget by remember { mutableStateOf<StockItem?>(null) }

    LaunchedEffect(openAddDialogInitially) {
        if (openAddDialogInitially) {
            editingStock = null
            showFormDialog = true
            onConsumeAddTrigger()
        }
    }

    val filteredStocks = remember(stocks, searchQuery, selectedCategory, statusFilter) {
        stocks.filter { item ->
            val matchesSearch = searchQuery.isBlank() ||
                item.brand.contains(searchQuery, ignoreCase = true) ||
                item.partName.contains(searchQuery, ignoreCase = true) ||
                item.category.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategory == "All" || item.category == selectedCategory
            val matchesStatus = statusFilter == null || item.computedStatus == statusFilter
            matchesSearch && matchesCategory && matchesStatus
        }
    }

    Scaffold(
        containerColor = NavyBlack,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingStock = null
                    showFormDialog = true
                },
                containerColor = AccentPurple,
                contentColor = TextPrimary,
                modifier = Modifier.testTag("add_stock_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add New Stock")
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
                GeminiAssistantBar(
                    isProcessing = isGeminiProcessing || isSubmitting,
                    onRunCommand = onRunGeminiCommand
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search brand, item, or category...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("stock_search_input"),
                        shape = RoundedCornerShape(14.dp)
                    )
                    OutlinedButton(
                        onClick = onLoadSampleItems,
                        enabled = !isSubmitting,
                        border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.6f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                        modifier = Modifier
                            .heightIn(min = 52.dp)
                            .testTag("load_sample_items_button")
                    ) {
                        Icon(Icons.Rounded.PlaylistAdd, contentDescription = "Load Sample Items", tint = AccentBlue)
                        Spacer(Modifier.width(4.dp))
                        Text("Samples", color = TextPrimary, maxLines = 1)
                    }
                }
            }

            // Status Filters (All / Low Stock / Empty Stock)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = statusFilter == null,
                        onClick = { statusFilter = null },
                        label = { Text("All (${stocks.size})") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentPurple, selectedLabelColor = TextPrimary)
                    )
                    FilterChip(
                        selected = statusFilter == StockStatus.LOW_STOCK,
                        onClick = { statusFilter = if (statusFilter == StockStatus.LOW_STOCK) null else StockStatus.LOW_STOCK },
                        label = { Text("Low Stock (${stocks.count { it.computedStatus == StockStatus.LOW_STOCK }})") },
                        modifier = Modifier.testTag("filter_low_stock"),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentPurple, selectedLabelColor = TextPrimary)
                    )
                    FilterChip(
                        selected = statusFilter == StockStatus.EMPTY_STOCK,
                        onClick = { statusFilter = if (statusFilter == StockStatus.EMPTY_STOCK) null else StockStatus.EMPTY_STOCK },
                        label = { Text("Empty Stock (${stocks.count { it.computedStatus == StockStatus.EMPTY_STOCK }})") },
                        modifier = Modifier.testTag("filter_empty_stock"),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentPurple, selectedLabelColor = TextPrimary)
                    )
                }
            }

            // Category Filter Chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val cats = listOf("All") + CatalogConstants.STOCK_CATEGORIES
                    cats.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentBlue, selectedLabelColor = TextPrimary)
                        )
                    }
                }
            }

            if (filteredStocks.isEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        EmptyStateCard(
                            icon = Icons.Rounded.Inventory2,
                            title = "No Stock Found",
                            description = "Tap Samples to load Basmati Rice 25kg, Sunflower Oil 1L & Sugar 1kg, or tap + to add an item."
                        )
                        Button(
                            onClick = onLoadSampleItems,
                            enabled = !isSubmitting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("empty_load_samples_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                        ) {
                            Icon(Icons.Rounded.PlaylistAdd, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Load Sample Items (Rice, Oil, Sugar)")
                        }
                    }
                }
            } else {
                items(filteredStocks, key = { it.id }) { stock ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("stock_card_${stock.id}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = NavyCard),
                        border = BorderStroke(1.dp, NavyBorder.copy(alpha = 0.7f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("${stock.brand} • ${stock.partName}", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                    Text("${stock.category} • Min Stock: ${stock.lowStockLimit}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                }
                                StatusBadge(label = stock.computedStatus.label)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Unit Price", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                    Text(
                                        formatInr(stock.unitPrice),
                                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono),
                                        color = TextPrimary
                                    )
                                }
                                Column {
                                    Text("Stock Value", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                    Text(
                                        formatInr(stock.stockValue),
                                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono),
                                        color = AccentBlue
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { onAdjustQuantity(stock, -1L) },
                                        enabled = stock.quantity > 0 && !isSubmitting
                                    ) {
                                        Icon(Icons.Rounded.Remove, contentDescription = "Decrease quantity", tint = TextPrimary)
                                    }
                                    Text(
                                        text = "${stock.quantity}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
                                        color = TextPrimary
                                    )
                                    IconButton(
                                        onClick = { onAdjustQuantity(stock, 1L) },
                                        enabled = !isSubmitting
                                    ) {
                                        Icon(Icons.Rounded.Add, contentDescription = "Increase quantity", tint = AccentPurple)
                                    }
                                }
                            }

                            // Quick Sale (-1 / Custom soldQty) & Set Stock Actions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onRecordQuickSale(stock, 1L) {} },
                                    enabled = stock.quantity > 0 && !isSubmitting,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("quick_sale_one_${stock.id}")
                                ) {
                                    Text("-1 Sale", maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = { customSaleTarget = stock },
                                    enabled = stock.quantity > 0 && !isSubmitting,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                    border = BorderStroke(1.dp, StatusAmber.copy(alpha = 0.7f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("custom_sale_${stock.id}")
                                ) {
                                    Icon(Icons.Rounded.PointOfSale, contentDescription = null, tint = StatusAmber)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Sell Qty", color = TextPrimary, maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = { exactStockTarget = stock },
                                    enabled = !isSubmitting,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                    border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.7f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("set_stock_${stock.id}")
                                ) {
                                    Icon(Icons.Rounded.Tune, contentDescription = null, tint = AccentBlue)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Set Stock", color = TextPrimary, maxLines = 1)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Updated by ${stock.updatedBy.ifBlank { actorName }} • ${formatIstDateTime(stock.updatedAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted,
                                    modifier = Modifier.weight(1f)
                                )
                                Row {
                                    IconButton(
                                        onClick = {
                                            editingStock = stock
                                            showFormDialog = true
                                        },
                                        modifier = Modifier.testTag("edit_stock_${stock.id}")
                                    ) {
                                        Icon(Icons.Rounded.Edit, contentDescription = "Edit stock", tint = AccentBlue)
                                    }
                                    if (isOwner) {
                                        IconButton(
                                            onClick = { deletingStock = stock },
                                            modifier = Modifier.testTag("delete_stock_${stock.id}")
                                        ) {
                                            Icon(Icons.Rounded.Delete, contentDescription = "Delete stock", tint = StatusRed)
                                        }
                                    }
                                }
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

    if (showFormDialog) {
        StockFormDialog(
            initial = editingStock,
            defaultLowLimit = defaultLowLimit,
            isSubmitting = isSubmitting,
            onDismiss = { showFormDialog = false },
            onSave = { brand, partName, category, unitPrice, purchaseCost, quantity, lowLimit ->
                onSaveStock(
                    editingStock?.id,
                    brand,
                    partName,
                    category,
                    unitPrice,
                    purchaseCost,
                    quantity,
                    lowLimit
                ) {
                    showFormDialog = false
                }
            }
        )
    }

    customSaleTarget?.let { target ->
        NumericStockActionDialog(
            title = "Record Sale",
            subtitle = "${target.partName} (Available: ${target.quantity}, Min: ${target.lowStockLimit})",
            fieldLabel = "Sold Quantity",
            initialValue = "1",
            confirmLabel = "Confirm Sale",
            isSubmitting = isSubmitting,
            onDismiss = { customSaleTarget = null },
            onConfirm = { qty ->
                onRecordQuickSale(target, qty) {
                    customSaleTarget = null
                }
            }
        )
    }

    exactStockTarget?.let { target ->
        NumericStockActionDialog(
            title = "Set Exact Stock",
            subtitle = "${target.partName} (Current: ${target.quantity}, Min: ${target.lowStockLimit})",
            fieldLabel = "New Stock Quantity",
            initialValue = target.quantity.toString(),
            confirmLabel = "Update Stock",
            isSubmitting = isSubmitting,
            onDismiss = { exactStockTarget = null },
            onConfirm = { qty ->
                onSetExactStock(target, qty) {
                    exactStockTarget = null
                }
            }
        )
    }

    deletingStock?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingStock = null },
            containerColor = NavyCardElevated,
            title = { Text("Delete Stock Item?", color = TextPrimary) },
            text = { Text("Permanently remove ${target.brand} ${target.partName} from inventory?", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteStock(target.id)
                        deletingStock = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingStock = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun NumericStockActionDialog(
    title: String,
    subtitle: String,
    fieldLabel: String,
    initialValue: String,
    confirmLabel: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var qtyText by remember(initialValue) { mutableStateOf(initialValue) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyCardElevated,
        title = { Text(title, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it },
                    label = { Text(fieldLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("numeric_stock_action_input")
                )
                if (errorMsg != null) {
                    Text(errorMsg.orEmpty(), color = StatusRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = qtyText.trim().toLongOrNull()
                    if (parsed == null || parsed < 0L) {
                        errorMsg = "Enter a valid non-negative number."
                        return@Button
                    }
                    onConfirm(parsed)
                },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                modifier = Modifier.testTag("numeric_stock_action_confirm")
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun StockFormDialog(
    initial: StockItem?,
    defaultLowLimit: Long,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        brand: String,
        partName: String,
        category: String,
        unitPrice: Double,
        purchaseCost: Double?,
        quantity: Long,
        lowStockLimit: Long
    ) -> Unit
) {
    var brand by remember { mutableStateOf(initial?.brand ?: CatalogConstants.MOBILE_BRANDS.first()) }
    var partName by remember { mutableStateOf(initial?.partName ?: CatalogConstants.PART_TYPES.first()) }
    var category by remember { mutableStateOf(initial?.category ?: CatalogConstants.STOCK_CATEGORIES.first()) }
    var unitPriceText by remember { mutableStateOf(initial?.unitPrice?.takeIf { it > 0 }?.toString() ?: "") }
    var purchaseCostText by remember { mutableStateOf(initial?.purchaseCost?.takeIf { it > 0 }?.toString() ?: "") }
    var quantityText by remember { mutableStateOf(initial?.quantity?.toString() ?: "") }
    var lowLimitText by remember { mutableStateOf((initial?.lowStockLimit ?: defaultLowLimit).toString()) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val computedValue = (unitPriceText.toDoubleOrNull() ?: 0.0) * (quantityText.toLongOrNull() ?: 0L)
    val computedStatus = StockStatus.compute(quantityText.toLongOrNull() ?: 0L, lowLimitText.toLongOrNull() ?: defaultLowLimit)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyCardElevated,
        title = { Text(if (initial == null) "Add New Stock" else "Edit Stock", color = TextPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SearchableDropdownField(
                    label = "Brand / Group",
                    selectedValue = brand,
                    options = CatalogConstants.MOBILE_BRANDS,
                    onSelected = { brand = it },
                    testTag = "stock_brand_dropdown",
                    allowCustomValue = true
                )
                SearchableDropdownField(
                    label = "Item / Part Name",
                    selectedValue = partName,
                    options = CatalogConstants.PART_TYPES,
                    onSelected = { partName = it },
                    testTag = "stock_part_dropdown",
                    allowCustomValue = true
                )
                SearchableDropdownField(
                    label = "Category",
                    selectedValue = category,
                    options = CatalogConstants.STOCK_CATEGORIES,
                    onSelected = { category = it },
                    testTag = "stock_category_dropdown",
                    allowCustomValue = true
                )
                OutlinedTextField(
                    value = unitPriceText,
                    onValueChange = { unitPriceText = it },
                    label = { Text("Unit Price (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stock_unit_price_input")
                )
                OutlinedTextField(
                    value = purchaseCostText,
                    onValueChange = { purchaseCostText = it },
                    label = { Text("Purchase Cost (₹, optional for profit)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it },
                        label = { Text("Quantity") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("stock_quantity_input")
                    )
                    OutlinedTextField(
                        value = lowLimitText,
                        onValueChange = { lowLimitText = it },
                        label = { Text("Min Stock") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("stock_low_limit_input")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Stock Value: ${formatInr(computedValue)}", style = MaterialTheme.typography.labelLarge, color = AccentBlue)
                    StatusBadge(label = computedStatus.label)
                }

                if (validationError != null) {
                    Text(validationError.orEmpty(), color = StatusRed, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val price = unitPriceText.toDoubleOrNull() ?: 0.0
                    val cost = purchaseCostText.toDoubleOrNull()
                    val qty = quantityText.toLongOrNull()
                    val limit = lowLimitText.toLongOrNull() ?: defaultLowLimit
                    if (brand.isBlank() || partName.isBlank()) {
                        validationError = "Enter Brand and Item Name."
                        return@Button
                    }
                    if (price < 0.0) {
                        validationError = "Enter a valid unit price."
                        return@Button
                    }
                    if (qty == null || qty < 0L) {
                        validationError = "Enter a valid quantity."
                        return@Button
                    }
                    onSave(brand, partName, category, price, cost, qty, limit.coerceAtLeast(0L))
                },
                enabled = !isSubmitting,
                modifier = Modifier.testTag("save_stock_button"),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
            ) {
                Text("Save Stock")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
