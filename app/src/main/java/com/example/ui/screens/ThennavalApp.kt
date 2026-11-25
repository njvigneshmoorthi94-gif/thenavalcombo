package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddShoppingCart
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.AssignmentReturn
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.model.ShopSettings
import com.example.data.model.StockStatus
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.ui.components.StatusBadge
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.NavyBlack
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavyCardElevated
import com.example.ui.theme.NavySurface
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.StatusRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.ShopViewModel
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.delay

enum class ShopDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val ownerOnly: Boolean
) {
    DASHBOARD("dashboard", "Dashboard", Icons.Rounded.Dashboard, false),
    STOCK("stock", "Stock", Icons.Rounded.Inventory2, false),
    ORDERS("orders", "Orders", Icons.Rounded.AddShoppingCart, false),
    PAYMENTS("payments", "Payments", Icons.Rounded.Payments, false),
    RETURNS("returns", "Returns", Icons.Rounded.AssignmentReturn, false),
    REPORTS("reports", "Reports", Icons.Rounded.Assessment, true),
    SETTINGS("settings", "Shop Settings", Icons.Rounded.Settings, true),
    USERS("users", "Users", Icons.Rounded.Group, true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThennavalShopShell(
    viewModel: ShopViewModel,
    onRefreshApproval: () -> Unit,
    onSignOut: () -> Unit
) {
    val profileState by viewModel.userProfileState.collectAsStateWithLifecycle()

    when (val pState = profileState) {
        UiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NavyBlack),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = AccentPurple)
                    Text("Loading Thennaval Combo workspace...", color = TextSecondary)
                }
            }
        }
        is UiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NavyBlack)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(pState.message, color = StatusRed)
                    Button(onClick = onSignOut) { Text("Sign Out") }
                }
            }
        }
        is UiState.Success -> {
            val profile = pState.data
            if (profile == null || !profile.active) {
                PendingApprovalScreen(
                    profile = profile,
                    onRefreshApproval = onRefreshApproval,
                    onSignOut = onSignOut
                )
            } else {
                AuthenticatedShopContent(
                    profile = profile,
                    viewModel = viewModel,
                    onSignOut = onSignOut
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedShopContent(
    profile: UserProfile,
    viewModel: ShopViewModel,
    onSignOut: () -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ShopDestination.DASHBOARD.route

    val isOwner = profile.userRole == UserRole.OWNER
    val visibleDestinations = remember(isOwner) {
        ShopDestination.entries.filter { !it.ownerOnly || isOwner }
    }

    val stocksUi by viewModel.stocksState.collectAsStateWithLifecycle()
    val ordersUi by viewModel.ordersState.collectAsStateWithLifecycle()
    val paymentsUi by viewModel.paymentsState.collectAsStateWithLifecycle()
    val returnsUi by viewModel.returnsState.collectAsStateWithLifecycle()
    val effectiveSettings by viewModel.effectiveSettingsState.collectAsStateWithLifecycle()
    val usersUi by viewModel.allUsersState.collectAsStateWithLifecycle()
    val notificationsUi by viewModel.notificationsState.collectAsStateWithLifecycle()
    val metrics by viewModel.dashboardMetrics.collectAsStateWithLifecycle()
    val dateFilter by viewModel.selectedDateFilter.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()
    val isGeminiProcessing by viewModel.isGeminiProcessing.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()

    val stocks = (stocksUi as? UiState.Success)?.data ?: emptyList()
    val orders = (ordersUi as? UiState.Success)?.data ?: emptyList()
    val payments = (paymentsUi as? UiState.Success)?.data ?: emptyList()
    val returns = (returnsUi as? UiState.Success)?.data ?: emptyList()
    val users = (usersUi as? UiState.Success)?.data ?: emptyList()
    val notifications = (notificationsUi as? UiState.Success)?.data ?: emptyList()

    var pendingOpenAddStock by remember { mutableStateOf(false) }
    var pendingOpenNewOrder by remember { mutableStateOf(false) }
    var pendingOpenCollectPayment by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showQuickSettingsModal by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(4000)
            viewModel.clearFeedback()
        }
    }

    LaunchedEffect(
        isOwner,
        effectiveSettings.emailWebhookUrl,
        effectiveSettings.lastWeeklyReportKey,
        effectiveSettings.weeklyEmailReport,
        orders.size,
        payments.size,
        stocks.size
    ) {
        if (isOwner) {
            viewModel.maybeAutoDispatchMondayReport(
                settings = effectiveSettings,
                orders = orders,
                payments = payments,
                stocks = stocks,
                actorName = profile.name
            )
        }
    }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(ShopDestination.DASHBOARD.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    if (currentRoute != ShopDestination.DASHBOARD.route) {
        BackHandler { navigateTo(ShopDestination.DASHBOARD.route) }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(NavyBlack)) {
        val useRail = maxWidth >= 680.dp
        val primaryBottomTabs = visibleDestinations.take(4)
        val overflowDestinations = visibleDestinations.drop(4)

        Scaffold(
            containerColor = NavyBlack,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = NavySurface,
                        titleContentColor = TextPrimary
                    ),
                    title = {
                        Column {
                            Text(
                                text = effectiveSettings.shopName.ifBlank { "Thennaval Combo" },
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Sync,
                                    contentDescription = "Real-time sync active",
                                    tint = StatusEmerald,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "${profile.name} • @2026 thennaval powered by suwa",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    },
                    actions = {
                        StatusBadge(label = profile.userRole.displayLabel)
                        IconButton(
                            onClick = { showQuickSettingsModal = true },
                            modifier = Modifier.testTag("quick_settings_modal_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Alert & Webhook Settings",
                                tint = AccentBlue
                            )
                        }
                        if (!useRail && overflowDestinations.size > 1) {
                            Box {
                                IconButton(
                                    onClick = { showOverflowMenu = true },
                                    modifier = Modifier.testTag("more_menu_button")
                                ) {
                                    Icon(Icons.Rounded.MoreHoriz, contentDescription = "More sections", tint = TextPrimary)
                                }
                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false },
                                    containerColor = NavyCardElevated
                                ) {
                                    overflowDestinations.forEach { dest ->
                                        DropdownMenuItem(
                                            text = { Text(dest.label, color = TextPrimary) },
                                            leadingIcon = { Icon(dest.icon, contentDescription = null, tint = AccentPurple) },
                                            onClick = {
                                                showOverflowMenu = false
                                                navigateTo(dest.route)
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Logout", color = StatusRed) },
                                        leadingIcon = { Icon(Icons.Rounded.Logout, contentDescription = null, tint = StatusRed) },
                                        onClick = {
                                            showOverflowMenu = false
                                            showLogoutDialog = true
                                        }
                                    )
                                }
                            }
                        } else {
                            IconButton(
                                onClick = { showLogoutDialog = true },
                                modifier = Modifier.testTag("logout_button")
                            ) {
                                Icon(Icons.Rounded.Logout, contentDescription = "Logout", tint = TextSecondary)
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = NavySurface,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        primaryBottomTabs.forEach { dest ->
                            NavigationBarItem(
                                selected = currentRoute == dest.route,
                                onClick = { navigateTo(dest.route) },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label, maxLines = 1) },
                                modifier = Modifier.testTag("nav_${dest.route}"),
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = TextPrimary,
                                    selectedTextColor = AccentPurple,
                                    indicatorColor = AccentPurple.copy(alpha = 0.25f),
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                )
                            )
                        }
                        if (overflowDestinations.size == 1) {
                            val last = overflowDestinations.first()
                            NavigationBarItem(
                                selected = currentRoute == last.route,
                                onClick = { navigateTo(last.route) },
                                icon = { Icon(last.icon, contentDescription = last.label) },
                                label = { Text(last.label, maxLines = 1) },
                                modifier = Modifier.testTag("nav_${last.route}"),
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = TextPrimary,
                                    selectedTextColor = AccentPurple,
                                    indicatorColor = AccentPurple.copy(alpha = 0.25f),
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = NavySurface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        visibleDestinations.forEach { dest ->
                            NavigationRailItem(
                                selected = currentRoute == dest.route,
                                onClick = { navigateTo(dest.route) },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("rail_${dest.route}"),
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = TextPrimary,
                                    selectedTextColor = AccentPurple,
                                    indicatorColor = AccentPurple.copy(alpha = 0.25f),
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                )
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        NavigationRailItem(
                            selected = false,
                            onClick = { showLogoutDialog = true },
                            icon = { Icon(Icons.Rounded.Logout, contentDescription = "Logout", tint = StatusRed) },
                            label = { Text("Logout", color = StatusRed) }
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    AnimatedVisibility(visible = feedback != null) {
                        feedback?.let { msg ->
                            val color = if (msg.isError) StatusRed else StatusEmerald
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .testTag("status_feedback_banner"),
                                shape = RoundedCornerShape(12.dp),
                                color = color.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(msg.message, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { viewModel.clearFeedback() }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Dismiss", tint = TextPrimary)
                                    }
                                }
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = ShopDestination.DASHBOARD.route,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable(ShopDestination.DASHBOARD.route) {
                            DashboardScreen(
                                metrics = metrics,
                                selectedFilter = dateFilter,
                                onFilterChange = viewModel::setDateFilter,
                                recentOrders = orders,
                                watchlistStocks = stocks.filter {
                                    it.computedStatus == StockStatus.LOW_STOCK || it.computedStatus == StockStatus.EMPTY_STOCK
                                },
                                isSubmitting = isSubmitting,
                                isGeminiProcessing = isGeminiProcessing,
                                onRunGeminiCommand = { commandText, onConsumed ->
                                    viewModel.executeGeminiCommand(
                                        commandText = commandText,
                                        stocks = stocks,
                                        orders = orders,
                                        payments = payments,
                                        settings = effectiveSettings,
                                        actorName = profile.name,
                                        onCommandConsumed = onConsumed
                                    )
                                },
                                onSendWeeklyReportNow = {
                                    viewModel.sendWeeklyReportNow(
                                        settings = effectiveSettings,
                                        orders = orders,
                                        payments = payments,
                                        stocks = stocks,
                                        actorName = profile.name
                                    )
                                },
                                onQuickNewOrder = {
                                    pendingOpenNewOrder = true
                                    navigateTo(ShopDestination.ORDERS.route)
                                },
                                onQuickAddStock = {
                                    pendingOpenAddStock = true
                                    navigateTo(ShopDestination.STOCK.route)
                                },
                                onQuickCollectPayment = {
                                    pendingOpenCollectPayment = true
                                    navigateTo(ShopDestination.PAYMENTS.route)
                                },
                                onNavigateToOrders = { navigateTo(ShopDestination.ORDERS.route) },
                                onNavigateToStock = { navigateTo(ShopDestination.STOCK.route) }
                            )
                        }
                        composable(ShopDestination.STOCK.route) {
                            StockScreen(
                                stocks = stocks,
                                isOwner = isOwner,
                                actorName = profile.name,
                                defaultLowLimit = effectiveSettings.lowStockLimit,
                                isSubmitting = isSubmitting,
                                isGeminiProcessing = isGeminiProcessing,
                                openAddDialogInitially = pendingOpenAddStock,
                                onConsumeAddTrigger = { pendingOpenAddStock = false },
                                onSaveStock = { id, brand, part, cat, price, cost, qty, limit, onDone ->
                                    viewModel.saveStock(id, brand, part, cat, price, cost, qty, limit, profile.name, onDone)
                                },
                                onAdjustQuantity = { item, delta ->
                                    viewModel.adjustStockQuantity(item, delta, profile.name)
                                },
                                onRecordQuickSale = { item, soldQty, onDone ->
                                    viewModel.recordQuickSale(item, soldQty, profile.name, onDone)
                                },
                                onSetExactStock = { item, exactQty, onDone ->
                                    viewModel.setExactStock(item, exactQty, profile.name, onDone)
                                },
                                onLoadSampleItems = {
                                    viewModel.loadSampleItems(profile.name, effectiveSettings.lowStockLimit)
                                },
                                onRunGeminiCommand = { commandText, onConsumed ->
                                    viewModel.executeGeminiCommand(
                                        commandText = commandText,
                                        stocks = stocks,
                                        orders = orders,
                                        payments = payments,
                                        settings = effectiveSettings,
                                        actorName = profile.name,
                                        onCommandConsumed = onConsumed
                                    )
                                },
                                onDeleteStock = viewModel::deleteStock
                            )
                        }
                        composable(ShopDestination.ORDERS.route) {
                            OrdersScreen(
                                orders = orders,
                                stocks = stocks,
                                actorName = profile.name,
                                isOwner = isOwner,
                                isSubmitting = isSubmitting,
                                openCreateDialogInitially = pendingOpenNewOrder,
                                onConsumeCreateTrigger = { pendingOpenNewOrder = false },
                                onCreateOrder = { cust, mob, stockId, qty, price, disc, paid, method, labour, onDone ->
                                    viewModel.createOrder(cust, mob, stockId, qty, price, disc, paid, method, labour, profile.name, onDone)
                                },
                                onDeleteOrder = viewModel::deleteOrder
                            )
                        }
                        composable(ShopDestination.PAYMENTS.route) {
                            PaymentsScreen(
                                payments = payments,
                                orders = orders,
                                isSubmitting = isSubmitting,
                                openCollectDialogInitially = pendingOpenCollectPayment,
                                onConsumeCollectTrigger = { pendingOpenCollectPayment = false },
                                onCollectPayment = { orderId, amt, method, notes, onDone ->
                                    viewModel.collectPayment(orderId, amt, method, notes, profile.name, onDone)
                                }
                            )
                        }
                        composable(ShopDestination.RETURNS.route) {
                            ReturnsScreen(
                                returns = returns,
                                orders = orders,
                                isSubmitting = isSubmitting,
                                onProcessReturn = { order, qty, reason, refund, status, onDone ->
                                    viewModel.processReturn(order, qty, reason, refund, status, profile.name, onDone)
                                }
                            )
                        }
                        composable(ShopDestination.REPORTS.route) {
                            ReportsScreen(
                                selectedFilter = dateFilter,
                                onFilterChange = viewModel::setDateFilter,
                                stocks = stocks,
                                orders = orders,
                                payments = payments,
                                returns = returns,
                                onBuildCsv = { filter ->
                                    viewModel.buildCsvReport(filter, stocks, orders, payments, returns)
                                },
                                onShowMessage = { /* surfaced by OS chooser */ }
                            )
                        }
                        composable(ShopDestination.SETTINGS.route) {
                            ShopSettingsScreen(
                                settings = effectiveSettings,
                                notifications = notifications,
                                isSubmitting = isSubmitting,
                                onSaveSettings = { updated ->
                                    viewModel.saveShopSettings(updated, profile.name)
                                },
                                onSendTestEmail = { currentSettings ->
                                    viewModel.sendTestEmail(currentSettings)
                                },
                                onSendWeeklyReportNow = { currentSettings ->
                                    viewModel.sendWeeklyReportNow(
                                        settings = currentSettings,
                                        orders = orders,
                                        payments = payments,
                                        stocks = stocks,
                                        actorName = profile.name
                                    )
                                }
                            )
                        }
                        composable(ShopDestination.USERS.route) {
                            UsersScreen(
                                users = users,
                                currentUserId = profile.userId.ifBlank { profile.id },
                                isSubmitting = isSubmitting,
                                onUpdateUser = viewModel::updateUserAccess,
                                onUpdateCredentials = viewModel::updateUserCredentials,
                                onCreateStaff = viewModel::createStaffUser
                            )
                        }
                    }
                }
            }
        }
    }

    if (showQuickSettingsModal) {
        QuickSettingsModalDialog(
            initialSettings = effectiveSettings,
            isSubmitting = isSubmitting,
            onDismiss = { showQuickSettingsModal = false },
            onSave = { updated ->
                viewModel.saveShopSettings(updated, profile.name) {
                    showQuickSettingsModal = false
                }
            },
            onSendTestAlert = { current ->
                viewModel.sendTestEmail(current)
            },
            onSendWeeklyReportNow = { current ->
                viewModel.sendWeeklyReportNow(
                    settings = current,
                    orders = orders,
                    payments = payments,
                    stocks = stocks,
                    actorName = profile.name
                )
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = NavyCardElevated,
            title = { Text("Sign Out?", color = TextPrimary) },
            text = { Text("Disconnect this device from your Thennaval Combo staff session?", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        onSignOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                ) {
                    Text("Sign Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun QuickSettingsModalDialog(
    initialSettings: ShopSettings,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSave: (ShopSettings) -> Unit,
    onSendTestAlert: (ShopSettings) -> Unit,
    onSendWeeklyReportNow: (ShopSettings) -> Unit
) {
    var webhookUrl by remember(initialSettings) { mutableStateOf(initialSettings.emailWebhookUrl) }
    var email by remember(initialSettings) { mutableStateOf(initialSettings.email) }
    var whatsapp by remember(initialSettings) { mutableStateOf(initialSettings.whatsapp) }
    var minStockText by remember(initialSettings) { mutableStateOf(initialSettings.lowStockLimit.toString()) }

    fun buildUpdated(): ShopSettings = initialSettings.copy(
        emailWebhookUrl = webhookUrl.trim(),
        email = email.trim(),
        whatsapp = whatsapp.trim(),
        lowStockLimit = (minStockText.toLongOrNull() ?: 5L).coerceAtLeast(0L)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyCardElevated,
        title = {
            Column {
                Text("Alert & Webhook Settings", color = TextPrimary)
                Text(
                    text = "@2026 thennaval powered by suwa",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = webhookUrl,
                    onValueChange = { webhookUrl = it },
                    label = { Text("Google Apps Script Web App URL") },
                    placeholder = { Text("PASTE_YOUR_GOOGLE_APPS_SCRIPT_WEB_APP_URL") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("modal_webhook_url_input")
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Alert Email") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("modal_alert_email_input")
                )
                OutlinedTextField(
                    value = whatsapp,
                    onValueChange = { whatsapp = it },
                    label = { Text("Alert WhatsApp Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("modal_alert_whatsapp_input")
                )
                OutlinedTextField(
                    value = minStockText,
                    onValueChange = { minStockText = it },
                    label = { Text("Default Minimum Stock Threshold") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("modal_min_stock_input")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onSendTestAlert(buildUpdated()) },
                        enabled = !isSubmitting,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Test Alert", maxLines = 1)
                    }
                    Button(
                        onClick = { onSendWeeklyReportNow(buildUpdated()) },
                        enabled = !isSubmitting,
                        colors = ButtonDefaults.buttonColors(containerColor = StatusEmerald),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("modal_send_weekly_report_button")
                    ) {
                        Icon(Icons.Rounded.Email, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Weekly", maxLines = 1)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(buildUpdated()) },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                modifier = Modifier.testTag("modal_save_settings_button")
            ) {
                Text("Save Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
