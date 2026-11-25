package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.GeminiAssistantClient
import com.example.data.ai.GeminiCommandAction
import com.example.data.firebase.ShopRepository
import com.example.data.firebase.StockAlertOutcome
import com.example.data.model.DateRangeFilter
import com.example.data.model.NotificationLog
import com.example.data.model.PaymentRecord
import com.example.data.model.ReturnRecord
import com.example.data.model.ShopOrder
import com.example.data.model.ShopSettings
import com.example.data.model.StockItem
import com.example.data.model.StockStatus
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class DashboardMetrics(
    val totalSales: Double = 0.0,
    val totalOrders: Int = 0,
    val completedOrders: Int = 0,
    val unpaidOrders: Int = 0,
    val returnedOrders: Int = 0,
    val collectedPayments: Double = 0.0,
    val pendingBalance: Double = 0.0,
    val returnAmount: Double = 0.0,
    val totalProfit: Double? = null,
    val lowStockCount: Int = 0,
    val emptyStockCount: Int = 0,
    val stockValuation: Double = 0.0,
    val dailySeries: List<Pair<String, Double>> = emptyList()
)

data class BannerFeedback(
    val message: String,
    val isError: Boolean
)

class ShopViewModel(
    private val repository: ShopRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _selectedDateFilter = MutableStateFlow(DateRangeFilter.TODAY)
    val selectedDateFilter: StateFlow<DateRangeFilter> = _selectedDateFilter.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _isGeminiProcessing = MutableStateFlow(false)
    val isGeminiProcessing: StateFlow<Boolean> = _isGeminiProcessing.asStateFlow()

    private val _feedback = MutableStateFlow<BannerFeedback?>(null)
    val feedback: StateFlow<BannerFeedback?> = _feedback.asStateFlow()

    private val _localSettingsState = MutableStateFlow(repository.loadLocalSettings())

    private fun <T> Flow<T>.toUiState(tag: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(tag, "Firestore stream error", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Failed to sync data"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val userProfileState: StateFlow<UiState<UserProfile?>> =
        repository.observeUserProfile(currentUserId).toUiState("UserProfileVM")

    private val isActiveStaffFlow: Flow<Boolean> = userProfileState.map { state ->
        state is UiState.Success && state.data?.active == true
    }

    private val isOwnerFlow: Flow<Boolean> = userProfileState.map { state ->
        state is UiState.Success && state.data?.active == true && state.data.userRole == UserRole.OWNER
    }

    @Suppress("OPT_IN_USAGE")
    val stocksState: StateFlow<UiState<List<StockItem>>> = isActiveStaffFlow
        .flatMapLatest { active -> if (active) repository.observeStocks() else flowOf(emptyList()) }
        .toUiState("StocksVM")

    @Suppress("OPT_IN_USAGE")
    val ordersState: StateFlow<UiState<List<ShopOrder>>> = isActiveStaffFlow
        .flatMapLatest { active -> if (active) repository.observeOrders() else flowOf(emptyList()) }
        .toUiState("OrdersVM")

    @Suppress("OPT_IN_USAGE")
    val paymentsState: StateFlow<UiState<List<PaymentRecord>>> = isActiveStaffFlow
        .flatMapLatest { active -> if (active) repository.observePayments() else flowOf(emptyList()) }
        .toUiState("PaymentsVM")

    @Suppress("OPT_IN_USAGE")
    val returnsState: StateFlow<UiState<List<ReturnRecord>>> = isActiveStaffFlow
        .flatMapLatest { active -> if (active) repository.observeReturns() else flowOf(emptyList()) }
        .toUiState("ReturnsVM")

    @Suppress("OPT_IN_USAGE")
    val shopSettingsState: StateFlow<UiState<ShopSettings?>> = isActiveStaffFlow
        .flatMapLatest { active -> if (active) repository.observeShopSettings() else flowOf(null) }
        .toUiState("ShopSettingsVM")

    val effectiveSettingsState: StateFlow<ShopSettings> = combine(
        shopSettingsState,
        _localSettingsState
    ) { remoteUi, local ->
        val remote = (remoteUi as? UiState.Success)?.data
        if (remote == null) {
            local
        } else {
            remote.copy(
                shopName = remote.shopName.ifBlank { local.shopName },
                email = remote.email.trim().ifBlank { local.email },
                whatsapp = remote.whatsapp.trim().ifBlank { local.whatsapp },
                emailWebhookUrl = remote.emailWebhookUrl.trim().ifBlank { local.emailWebhookUrl }
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = repository.loadLocalSettings()
    )

    @Suppress("OPT_IN_USAGE")
    val allUsersState: StateFlow<UiState<List<UserProfile>>> = isOwnerFlow
        .flatMapLatest { owner -> if (owner) repository.observeAllUsers() else flowOf(emptyList()) }
        .toUiState("UsersVM")

    @Suppress("OPT_IN_USAGE")
    val notificationsState: StateFlow<UiState<List<NotificationLog>>> = isOwnerFlow
        .flatMapLatest { owner -> if (owner) repository.observeNotifications() else flowOf(emptyList()) }
        .toUiState("NotificationsVM")

    val dashboardMetrics: StateFlow<DashboardMetrics> = combine(
        stocksState,
        ordersState,
        paymentsState,
        returnsState,
        selectedDateFilter
    ) { stocksUi, ordersUi, paymentsUi, returnsUi, filter ->
        val stocks = (stocksUi as? UiState.Success)?.data ?: emptyList()
        val orders = (ordersUi as? UiState.Success)?.data ?: emptyList()
        val payments = (paymentsUi as? UiState.Success)?.data ?: emptyList()
        val returns = (returnsUi as? UiState.Success)?.data ?: emptyList()

        computeMetrics(stocks, orders, payments, returns, filter)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = DashboardMetrics()
    )

    fun setDateFilter(filter: DateRangeFilter) {
        _selectedDateFilter.value = filter
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    private fun applyAlertFeedback(defaultSuccessMessage: String, outcome: StockAlertOutcome) {
        _feedback.value = when {
            outcome.alertSent -> BannerFeedback(
                outcome.bannerMessage ?: "Alert sent to Gmail & WhatsApp",
                false
            )
            outcome.alertError != null -> BannerFeedback(outcome.alertError, true)
            else -> BannerFeedback(defaultSuccessMessage, false)
        }
    }

    fun saveStock(
        stockId: String?,
        brand: String,
        partName: String,
        category: String,
        unitPrice: Double,
        purchaseCost: Double?,
        quantity: Long,
        lowStockLimit: Long,
        actorName: String,
        onSuccess: () -> Unit = {}
    ) {
        if (_isSubmitting.value) return
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.saveStock(
                stockId, brand, partName, category, unitPrice, purchaseCost,
                quantity, lowStockLimit, actorName, currentUserId
            )
            _isSubmitting.value = false
            result.onSuccess { outcome ->
                applyAlertFeedback(
                    if (stockId == null) "Stock added & synced." else "Stock updated & synced.",
                    outcome
                )
                onSuccess()
            }.onFailure { err ->
                _feedback.value = BannerFeedback(err.localizedMessage ?: "Failed to save stock.", true)
            }
        }
    }

    fun adjustStockQuantity(stock: StockItem, delta: Long, actorName: String) {
        val nextQty = (stock.quantity + delta).coerceAtLeast(0L)
        if (_isSubmitting.value || nextQty == stock.quantity) return
        val action = if (delta < 0L) "sale" else "set_stock"
        val soldQty = if (delta < 0L) -delta else 0L
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.adjustStockQuantity(
                stock = stock,
                newQuantity = nextQty,
                actorName = actorName,
                actorUid = currentUserId,
                action = action,
                soldQty = soldQty
            )
            _isSubmitting.value = false
            res.onSuccess { outcome ->
                val defaultMsg = if (delta < 0L) {
                    "Sale recorded for ${stock.partName} (Stock: $nextQty)."
                } else {
                    "Updated ${stock.partName} stock to $nextQty."
                }
                applyAlertFeedback(defaultMsg, outcome)
            }.onFailure { err ->
                _feedback.value = BannerFeedback(err.localizedMessage ?: "Failed to update quantity.", true)
            }
        }
    }

    fun recordQuickSale(stock: StockItem, soldQty: Long, actorName: String, onSuccess: () -> Unit = {}) {
        if (_isSubmitting.value) return
        if (soldQty <= 0L) {
            _feedback.value = BannerFeedback("Sold quantity must be at least 1.", true)
            return
        }
        val nextQty = (stock.quantity - soldQty).coerceAtLeast(0L)
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.adjustStockQuantity(
                stock = stock,
                newQuantity = nextQty,
                actorName = actorName,
                actorUid = currentUserId,
                action = "sale",
                soldQty = soldQty
            )
            _isSubmitting.value = false
            res.onSuccess { outcome ->
                applyAlertFeedback("Sold $soldQty × ${stock.partName} (Remaining: $nextQty).", outcome)
                onSuccess()
            }.onFailure { err ->
                _feedback.value = BannerFeedback(err.localizedMessage ?: "Failed to record sale.", true)
            }
        }
    }

    fun setExactStock(stock: StockItem, exactQty: Long, actorName: String, onSuccess: () -> Unit = {}) {
        if (_isSubmitting.value) return
        val cleanQty = exactQty.coerceAtLeast(0L)
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.adjustStockQuantity(
                stock = stock,
                newQuantity = cleanQty,
                actorName = actorName,
                actorUid = currentUserId,
                action = "set_stock",
                soldQty = 0L
            )
            _isSubmitting.value = false
            res.onSuccess { outcome ->
                applyAlertFeedback("Set ${stock.partName} stock to $cleanQty.", outcome)
                onSuccess()
            }.onFailure { err ->
                _feedback.value = BannerFeedback(err.localizedMessage ?: "Failed to set stock.", true)
            }
        }
    }

    fun loadSampleItems(actorName: String, defaultMinStock: Long) {
        if (_isSubmitting.value) return
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.loadSampleItems(actorName, defaultMinStock)
            _isSubmitting.value = false
            res.onSuccess { added ->
                _feedback.value = BannerFeedback(
                    if (added > 0) "Added $added sample items to inventory."
                    else "Sample items already exist in inventory.",
                    false
                )
            }.onFailure { err ->
                _feedback.value = BannerFeedback(err.localizedMessage ?: "Failed to load sample items.", true)
            }
        }
    }

    fun executeGeminiCommand(
        commandText: String,
        stocks: List<StockItem>,
        orders: List<ShopOrder>,
        payments: List<PaymentRecord>,
        settings: ShopSettings,
        actorName: String,
        onCommandConsumed: () -> Unit = {}
    ) {
        if (_isGeminiProcessing.value || _isSubmitting.value) return
        viewModelScope.launch {
            _isGeminiProcessing.value = true
            val parsedResult = GeminiAssistantClient.parseInventoryCommand(
                commandText = commandText,
                stocks = stocks,
                defaultMinStock = settings.lowStockLimit
            )
            _isGeminiProcessing.value = false

            parsedResult.onFailure { err ->
                _feedback.value = BannerFeedback(
                    err.localizedMessage ?: "Gemini Assistant failed to process command.",
                    true
                )
            }.onSuccess { parsed ->
                when (parsed.action) {
                    GeminiCommandAction.WEEKLY_REPORT -> {
                        onCommandConsumed()
                        sendWeeklyReportNow(settings, orders, payments, stocks, actorName)
                    }
                    GeminiCommandAction.ADD_ITEM -> {
                        val newName = parsed.itemName.trim()
                        if (newName.isBlank()) {
                            _feedback.value = BannerFeedback("Specify an item name to add.", true)
                            return@onSuccess
                        }
                        onCommandConsumed()
                        saveStock(
                            stockId = null,
                            brand = "Thennaval",
                            partName = newName,
                            category = "Grocery",
                            unitPrice = parsed.unitPrice.coerceAtLeast(0.0),
                            purchaseCost = null,
                            quantity = parsed.quantity.coerceAtLeast(0L),
                            lowStockLimit = parsed.minStock.coerceAtLeast(0L),
                            actorName = actorName
                        )
                    }
                    GeminiCommandAction.SALE -> {
                        val matched = findMatchingStock(stocks, parsed.stockId, parsed.itemName)
                        if (matched == null) {
                            _feedback.value = BannerFeedback(
                                "Could not find item \"${parsed.itemName.ifBlank { commandText }}\" in inventory.",
                                true
                            )
                            return@onSuccess
                        }
                        onCommandConsumed()
                        recordQuickSale(
                            stock = matched,
                            soldQty = parsed.quantity.coerceAtLeast(1L),
                            actorName = actorName
                        )
                    }
                    GeminiCommandAction.SET_STOCK -> {
                        val matched = findMatchingStock(stocks, parsed.stockId, parsed.itemName)
                        if (matched == null) {
                            _feedback.value = BannerFeedback(
                                "Could not find item \"${parsed.itemName.ifBlank { commandText }}\" in inventory.",
                                true
                            )
                            return@onSuccess
                        }
                        onCommandConsumed()
                        setExactStock(
                            stock = matched,
                            exactQty = parsed.quantity.coerceAtLeast(0L),
                            actorName = actorName
                        )
                    }
                    GeminiCommandAction.UNKNOWN -> {
                        _feedback.value = BannerFeedback(
                            parsed.explanation.ifBlank {
                                "Try a command like \"Sold 2 Sunflower Oil\" or \"Set Sugar stock to 0\"."
                            },
                            true
                        )
                    }
                }
            }
        }
    }

    private fun findMatchingStock(
        stocks: List<StockItem>,
        stockId: String,
        itemName: String
    ): StockItem? {
        if (stockId.isNotBlank()) {
            stocks.firstOrNull { it.id == stockId }?.let { return it }
        }
        val needle = itemName.trim().lowercase(Locale.ROOT)
        if (needle.isBlank()) return null
        return stocks.firstOrNull { it.partName.equals(needle, ignoreCase = true) }
            ?: stocks.firstOrNull { "${it.brand} ${it.partName}".trim().equals(needle, ignoreCase = true) }
            ?: stocks.firstOrNull {
                it.partName.lowercase(Locale.ROOT).contains(needle) ||
                    needle.contains(it.partName.lowercase(Locale.ROOT))
            }
    }

    fun deleteStock(stockId: String) {
        if (_isSubmitting.value) return
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.deleteStock(stockId)
            _isSubmitting.value = false
            res.onSuccess {
                _feedback.value = BannerFeedback("Stock item deleted.", false)
            }.onFailure { err ->
                _feedback.value = BannerFeedback(err.localizedMessage ?: "Failed to delete stock.", true)
            }
        }
    }

    fun createOrder(
        customerName: String,
        mobile: String,
        selectedStockId: String,
        quantity: Long,
        unitPriceOverride: Double?,
        discount: Double,
        paidAmount: Double,
        paymentMethod: String,
        labourName: String,
        actorName: String,
        onSuccess: () -> Unit = {}
    ) {
        if (_isSubmitting.value) return
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.createOrder(
                customerName, mobile, selectedStockId, quantity, unitPriceOverride,
                discount, paidAmount, paymentMethod, labourName, actorName, currentUserId
            )
            _isSubmitting.value = false
            res.onSuccess { outcome ->
                applyAlertFeedback("Order confirmed & stock synced.", outcome)
                onSuccess()
            }.onFailure { err ->
                _feedback.value = BannerFeedback(err.localizedMessage ?: "Failed to create order.", true)
            }
        }
    }

    fun collectPayment(
        orderId: String,
        amount: Double,
        paymentMethod: String,
        notes: String,
        actorName: String,
        onSuccess: () -> Unit = {}
    ) {
        if (_isSubmitting.value) return
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.collectPayment(
                orderId, amount, paymentMethod, notes, actorName, currentUserId
            )
            _isSubmitting.value = false
            res.onSuccess {
                _feedback.value = BannerFeedback("Payment collected & synced.", false)
                onSuccess()
            }.onFailure { err ->
                _feedback.value = BannerFeedback(err.localizedMessage ?: "Failed to collect payment.", true)
            }
        }
    }

    fun processReturn(
        order: ShopOrder,
        returnQuantity: Long,
        reason: String,
        refundAmount: Double,
        returnStatus: String,
        actorName: String,
        onSuccess: () -> Unit = {}
    ) {
        if (_isSubmitting.value) return
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.processReturn(
                order, returnQuantity, reason, refundAmount, returnStatus, actorName, currentUserId
            )
            _isSubmitting.value = false
            res.onSuccess {
                _feedback.value = BannerFeedback("Return processed & inventory synced.", false)
                onSuccess()
            }.onFailure { err ->
                _feedback.value = BannerFeedback(err.localizedMessage ?: "Failed to process return.", true)
            }
        }
    }

    private var lastCheckedWeeklyKey: String? = null

    fun saveShopSettings(settings: ShopSettings, actorName: String, onSuccess: () -> Unit = {}) {
        if (_isSubmitting.value) return
        viewModelScope.launch {
            _isSubmitting.value = true
            val normalized = settings.copy(
                shopName = settings.shopName.trim().ifBlank { "Thennaval Combo" },
                ownerName = settings.ownerName.trim().ifBlank { actorName.ifBlank { "Owner" } },
                emailWebhookUrl = settings.emailWebhookUrl.trim()
            )
            val res = repository.saveShopSettings(normalized, actorName, currentUserId)
            _isSubmitting.value = false
            res.onSuccess {
                _localSettingsState.value = repository.loadLocalSettings()
                _feedback.value = BannerFeedback("Shop settings saved to Firebase & device.", false)
                onSuccess()
            }.onFailure { err ->
                _feedback.value = BannerFeedback(err.localizedMessage ?: "Failed to save shop settings.", true)
            }
        }
    }

    fun sendTestEmail(settings: ShopSettings) {
        if (_isSubmitting.value) return
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.sendTestEmail(settings)
            _isSubmitting.value = false
            res.onSuccess {
                _feedback.value = BannerFeedback("Alert sent to Gmail & WhatsApp", false)
            }.onFailure { err ->
                _feedback.value = BannerFeedback(err.localizedMessage ?: "Failed to send test alert.", true)
            }
        }
    }

    fun sendWeeklyReportNow(
        settings: ShopSettings?,
        orders: List<ShopOrder>,
        payments: List<PaymentRecord>,
        stocks: List<StockItem>,
        actorName: String
    ) {
        if (_isSubmitting.value) return
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.dispatchWeeklyReportNow(
                settings = settings,
                orders = orders,
                payments = payments,
                stocks = stocks,
                actorName = actorName,
                actorUid = currentUserId,
                onlyIfMonday8AmDue = false
            )
            _isSubmitting.value = false
            res.onSuccess {
                _feedback.value = BannerFeedback("Weekly report sent to Gmail & WhatsApp.", false)
            }.onFailure { err ->
                _feedback.value = BannerFeedback(err.localizedMessage ?: "Failed to send weekly report.", true)
            }
        }
    }

    fun maybeAutoDispatchMondayReport(
        settings: ShopSettings?,
        orders: List<ShopOrder>,
        payments: List<PaymentRecord>,
        stocks: List<StockItem>,
        actorName: String
    ) {
        if (settings == null || !settings.weeklyEmailReport || settings.emailWebhookUrl.isBlank()) return
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        if (dayOfWeek != Calendar.MONDAY || hour < 8) return
        val weekKey = "%04d-W%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.WEEK_OF_YEAR))
        if (settings.lastWeeklyReportKey == weekKey || lastCheckedWeeklyKey == weekKey) return

        lastCheckedWeeklyKey = weekKey
        viewModelScope.launch {
            val res = repository.dispatchWeeklyReportNow(
                settings = settings,
                orders = orders,
                payments = payments,
                stocks = stocks,
                actorName = actorName,
                actorUid = currentUserId,
                onlyIfMonday8AmDue = true
            )
            res.onSuccess { sent ->
                if (sent) {
                    _feedback.value = BannerFeedback("Monday 8:00 AM weekly report sent automatically.", false)
                }
            }.onFailure {
                lastCheckedWeeklyKey = null
            }
        }
    }

    fun updateUserAccess(
        targetUserId: String,
        role: UserRole,
        active: Boolean,
        name: String,
        username: String
    ) {
        if (_isSubmitting.value) return
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.updateUserAccess(targetUserId, role, active, name, username)
            _isSubmitting.value = false
            res.onSuccess {
                _feedback.value = BannerFeedback("Staff permissions updated.", false)
            }.onFailure { err ->
                _feedback.value = BannerFeedback(err.localizedMessage ?: "Failed to update user.", true)
            }
        }
    }

    fun updateUserCredentials(
        targetUserId: String,
        username: String,
        newPassword: String?,
        onSuccess: () -> Unit = {}
    ) {
        if (_isSubmitting.value) return
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.updateUserCredentials(targetUserId, username, newPassword)
            _isSubmitting.value = false
            res.onSuccess {
                _feedback.value = BannerFeedback("Staff username & password updated.", false)
                onSuccess()
            }.onFailure { err ->
                _feedback.value = BannerFeedback(err.localizedMessage ?: "Failed to update credentials.", true)
            }
        }
    }

    fun createStaffUser(
        name: String,
        username: String,
        password: String,
        role: UserRole,
        active: Boolean = true,
        onSuccess: () -> Unit = {}
    ) {
        if (_isSubmitting.value) return
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.createStaffUser(name, username, password, role, active)
            _isSubmitting.value = false
            res.onSuccess {
                _feedback.value = BannerFeedback("Staff account @$username created.", false)
                onSuccess()
            }.onFailure { err ->
                _feedback.value = BannerFeedback(err.localizedMessage ?: "Failed to create staff account.", true)
            }
        }
    }

    fun deleteOrder(orderId: String) {
        if (_isSubmitting.value) return
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.deleteOrder(orderId)
            _isSubmitting.value = false
            res.onSuccess {
                _feedback.value = BannerFeedback("Order deleted.", false)
            }.onFailure { err ->
                _feedback.value = BannerFeedback(err.localizedMessage ?: "Failed to delete order.", true)
            }
        }
    }

    fun buildCsvReport(
        filter: DateRangeFilter,
        stocks: List<StockItem>,
        orders: List<ShopOrder>,
        payments: List<PaymentRecord>,
        returns: List<ReturnRecord>
    ): String {
        val metrics = computeMetrics(stocks, orders, payments, returns, filter)
        val sb = StringBuilder()
        sb.appendLine("Thennaval Combo - ${filter.label} Report")
        sb.appendLine("Metric,Value")
        sb.appendLine("Total Sales,${metrics.totalSales}")
        sb.appendLine("Total Profit,${metrics.totalProfit?.toString() ?: "Not Recorded"}")
        sb.appendLine("Total Orders,${metrics.totalOrders}")
        sb.appendLine("Completed Orders,${metrics.completedOrders}")
        sb.appendLine("Unpaid/Partial Orders,${metrics.unpaidOrders}")
        sb.appendLine("Returned Orders,${metrics.returnedOrders}")
        sb.appendLine("Return Amount,${metrics.returnAmount}")
        sb.appendLine("Payment Collection,${metrics.collectedPayments}")
        sb.appendLine("Pending Balance,${metrics.pendingBalance}")
        sb.appendLine("Stock Valuation,${metrics.stockValuation}")
        sb.appendLine()
        sb.appendLine("Order Number,Customer,Mobile,Part,Final Amount,Paid,Balance,Status,Staff")
        filterByDate(orders, filter) { it.createdAt }.forEach { o ->
            sb.appendLine(
                "\"${o.orderNumber}\",\"${o.customerName}\",\"${o.mobile}\",\"${o.brandSummary} ${o.partSummary}\",${o.finalAmount},${o.paidAmount},${o.balanceAmount},\"${o.orderStatus}\",\"${o.labourName}\""
            )
        }
        return sb.toString()
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5000L

        fun startOfWindowMillis(filter: DateRangeFilter): Long {
            val tz = TimeZone.getTimeZone("Asia/Kolkata")
            val cal = Calendar.getInstance(tz)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            when (filter) {
                DateRangeFilter.TODAY -> Unit
                DateRangeFilter.WEEKLY -> cal.add(Calendar.DAY_OF_YEAR, -6)
                DateRangeFilter.MONTHLY -> cal.add(Calendar.DAY_OF_YEAR, -29)
            }
            return cal.timeInMillis
        }

        fun <T> filterByDate(
            items: List<T>,
            filter: DateRangeFilter,
            timestampSelector: (T) -> Timestamp?
        ): List<T> {
            val cutoff = startOfWindowMillis(filter)
            return items.filter { item ->
                val tsMillis = timestampSelector(item)?.let { it.seconds * 1000L } ?: System.currentTimeMillis()
                tsMillis >= cutoff
            }
        }

        fun computeMetrics(
            stocks: List<StockItem>,
            orders: List<ShopOrder>,
            payments: List<PaymentRecord>,
            returns: List<ReturnRecord>,
            filter: DateRangeFilter
        ): DashboardMetrics {
            val filteredOrders = filterByDate(orders, filter) { it.createdAt }
            val filteredPayments = filterByDate(payments, filter) { it.paymentDate ?: it.createdAt }
            val filteredReturns = filterByDate(returns, filter) { it.returnDate ?: it.createdAt }

            val totalSales = filteredOrders.sumOf { it.finalAmount }
            val completedOrders = filteredOrders.count { it.orderStatus == "Completed" }
            val unpaidOrders = filteredOrders.count { it.orderStatus == "Unpaid" || it.orderStatus == "Partial Payment" }
            val returnedOrders = filteredOrders.count { it.orderStatus == "Returned" }
            val pendingBalance = filteredOrders.sumOf { it.balanceAmount }
            val collectedPayments = filteredPayments.sumOf { it.amount }
            val returnAmount = filteredReturns.filter { it.status == "Processed" }.sumOf { it.refundAmount }

            var hasRecordedCost = false
            var totalCost = 0.0
            filteredOrders.filter { it.orderStatus != "Returned" }.forEach { order ->
                order.items.forEach { item ->
                    if (item.purchaseCost > 0.0) {
                        hasRecordedCost = true
                        totalCost += item.purchaseCost * item.quantity
                    }
                }
            }
            val netActiveSales = filteredOrders.filter { it.orderStatus != "Returned" }.sumOf { it.finalAmount }
            val totalProfit = if (hasRecordedCost) (netActiveSales - totalCost) else null

            val lowStockCount = stocks.count { it.computedStatus == StockStatus.LOW_STOCK }
            val emptyStockCount = stocks.count { it.computedStatus == StockStatus.EMPTY_STOCK }
            val stockValuation = stocks.sumOf { it.stockValue }

            val tz = TimeZone.getTimeZone("Asia/Kolkata")
            val dayLabels = mutableListOf<Pair<String, Double>>()
            val dayFormat = java.text.SimpleDateFormat("dd/MM", Locale.ENGLISH).apply {
                timeZone = tz
            }
            val bucketCount = when (filter) {
                DateRangeFilter.TODAY -> 6
                DateRangeFilter.WEEKLY -> 7
                DateRangeFilter.MONTHLY -> 6
            }
            if (filter == DateRangeFilter.WEEKLY) {
                for (i in 6 downTo 0) {
                    val cal = Calendar.getInstance(tz).apply {
                        add(Calendar.DAY_OF_YEAR, -i)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val start = cal.timeInMillis
                    val end = start + 24 * 60 * 60 * 1000L
                    val sum = orders.filter {
                        val t = it.createdAt?.let { ts -> ts.seconds * 1000L } ?: System.currentTimeMillis()
                        t in start until end
                    }.sumOf { it.finalAmount }
                    dayLabels.add(dayFormat.format(cal.time) to sum)
                }
            } else {
                val windowStart = startOfWindowMillis(filter)
                val span = (System.currentTimeMillis() - windowStart).coerceAtLeast(1L)
                val step = (span / bucketCount).coerceAtLeast(1L)
                for (b in 0 until bucketCount) {
                    val bStart = windowStart + b * step
                    val bEnd = if (b == bucketCount - 1) Long.MAX_VALUE else windowStart + (b + 1) * step
                    val sum = filteredOrders.filter {
                        val t = it.createdAt?.let { ts -> ts.seconds * 1000L } ?: System.currentTimeMillis()
                        t in bStart until bEnd
                    }.sumOf { it.finalAmount }
                    val label = if (filter == DateRangeFilter.TODAY) {
                        val c = Calendar.getInstance(tz).apply { timeInMillis = bStart }
                        "${c.get(Calendar.HOUR_OF_DAY)}h"
                    } else {
                        "W${b + 1}"
                    }
                    dayLabels.add(label to sum)
                }
            }

            return DashboardMetrics(
                totalSales = totalSales,
                totalOrders = filteredOrders.size,
                completedOrders = completedOrders,
                unpaidOrders = unpaidOrders,
                returnedOrders = returnedOrders,
                collectedPayments = collectedPayments,
                pendingBalance = pendingBalance,
                returnAmount = returnAmount,
                totalProfit = totalProfit,
                lowStockCount = lowStockCount,
                emptyStockCount = emptyStockCount,
                stockValuation = stockValuation,
                dailySeries = dayLabels
            )
        }
    }
}
