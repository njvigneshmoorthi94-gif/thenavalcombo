package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class UserRole(val wireValue: String, val displayLabel: String) {
    OWNER("owner", "Owner"),
    LABOUR("labour", "Labour");

    companion object {
        fun fromWire(value: String?): UserRole =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: LABOUR
    }
}

enum class StockStatus(val label: String) {
    IN_STOCK("In Stock"),
    LOW_STOCK("Low Stock"),
    EMPTY_STOCK("Empty Stock");

    companion object {
        fun compute(quantity: Long, lowStockLimit: Long): StockStatus = when {
            quantity <= 0L -> EMPTY_STOCK
            quantity <= lowStockLimit -> LOW_STOCK
            else -> IN_STOCK
        }

        fun fromWire(value: String?): StockStatus =
            entries.firstOrNull { it.label.equals(value, ignoreCase = true) } ?: IN_STOCK
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val role: String = "labour",
    val name: String = "",
    val email: String = "",
    val active: Boolean = false,
    val passcodeHash: String = "",
    val passcodeSalt: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val userRole: UserRole get() = UserRole.fromWire(role)
}

@Keep
@IgnoreExtraProperties
data class ShopSettings(
    @DocumentId val id: String = "main_shop",
    val shopName: String = "Thennaval Combo",
    val address: String = "",
    val mobile: String = "",
    val ownerName: String = "",
    val email: String = "mechmoorthi007@gmail.com",
    val whatsapp: String = "918526820655",
    val emailWebhookUrl: String = "",
    val lastWeeklyReportKey: String = "",
    val lowStockLimit: Long = 5L,
    val dailyMorningAlert: Boolean = true,
    val dailyNightAlert: Boolean = true,
    val weeklyEmailReport: Boolean = true,
    val updatedBy: String = "",
    val updatedByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StockItem(
    @DocumentId val id: String = "",
    val brand: String = "",
    val partName: String = "",
    val category: String = "",
    val unitPrice: Double = 0.0,
    val purchaseCost: Double = 0.0,
    val quantity: Long = 0L,
    val lowStockLimit: Long = 5L,
    val status: String = StockStatus.IN_STOCK.label,
    val updatedBy: String = "",
    val updatedByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val stockValue: Double get() = unitPrice * quantity
    val computedStatus: StockStatus get() = StockStatus.compute(quantity, lowStockLimit)
    val restockNeeded: Long get() = if (quantity <= lowStockLimit) (lowStockLimit * 2 - quantity).coerceAtLeast(1L) else 0L
}

@Keep
@IgnoreExtraProperties
data class OrderLineItem(
    val stockId: String = "",
    val brand: String = "",
    val partName: String = "",
    val quantity: Long = 1L,
    val unitPrice: Double = 0.0,
    val purchaseCost: Double = 0.0,
    val lineTotal: Double = 0.0
)

@Keep
@IgnoreExtraProperties
data class ShopOrder(
    @DocumentId val id: String = "",
    val orderNumber: String = "",
    val customerName: String = "",
    val mobile: String = "",
    val items: List<OrderLineItem> = emptyList(),
    val brandSummary: String = "",
    val partSummary: String = "",
    val totalQuantity: Long = 0L,
    val totalAmount: Double = 0.0,
    val discount: Double = 0.0,
    val finalAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val balanceAmount: Double = 0.0,
    val orderStatus: String = "Unpaid",
    val paymentStatus: String = "Unpaid",
    val labourName: String = "",
    val createdBy: String = "",
    val createdByUid: String = "",
    val updatedBy: String = "",
    val updatedByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PaymentRecord(
    @DocumentId val id: String = "",
    val orderId: String = "",
    val orderNumber: String = "",
    val customerName: String = "",
    val mobile: String = "",
    val amount: Double = 0.0,
    val paymentMethod: String = "Cash",
    val collectedBy: String = "",
    val collectedByUid: String = "",
    val notes: String = "",
    @ServerTimestamp val paymentDate: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ReturnRecord(
    @DocumentId val id: String = "",
    val orderId: String = "",
    val orderNumber: String = "",
    val customerName: String = "",
    val mobile: String = "",
    val stockId: String = "",
    val product: String = "",
    val quantity: Long = 1L,
    val reason: String = "",
    val refundAmount: Double = 0.0,
    val status: String = "Processed",
    val processedBy: String = "",
    val processedByUid: String = "",
    @ServerTimestamp val returnDate: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class NotificationLog(
    @DocumentId val id: String = "",
    val type: String = "",
    val channel: String = "",
    val message: String = "",
    val status: String = "",
    val recipient: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

enum class DateRangeFilter(val label: String) {
    TODAY("Today"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly")
}

fun formatInr(amount: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    format.maximumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
    return format.format(amount)
}

fun formatIstDateTime(timestamp: Timestamp?): String {
    if (timestamp == null) return "Just now"
    val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.ENGLISH)
    sdf.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
    return sdf.format(Date(timestamp.seconds * 1000L))
}

fun formatIstDate(timestamp: Timestamp?): String {
    if (timestamp == null) return "Today"
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
    sdf.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
    return sdf.format(Date(timestamp.seconds * 1000L))
}
