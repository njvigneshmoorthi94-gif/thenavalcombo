package com.example.data.firebase

import android.util.Log
import com.example.data.local.SettingsPrefs
import com.example.data.model.ShopOrder
import com.example.data.model.StockItem
import com.example.data.model.StockStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object FreeEmailDispatcher {

    private const val TAG = "FreeEmailDispatcher"
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun isSafeWebhookUrl(rawUrl: String): Boolean {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty() || trimmed.length > 300) return false
        if (trimmed.equals("PASTE_YOUR_GOOGLE_APPS_SCRIPT_WEB_APP_URL", ignoreCase = true)) return false
        return try {
            val parsed = URL(trimmed)
            if (!parsed.protocol.equals("https", ignoreCase = true)) return false
            val host = parsed.host.lowercase(Locale.ROOT)
            if (host.isBlank() ||
                host == "localhost" ||
                host == "127.0.0.1" ||
                host == "::1" ||
                host == "169.254.169.254" ||
                host.endsWith(".local")
            ) {
                return false
            }
            if (isPrivateOrLoopbackLiteral(host)) return false
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun isPrivateOrLoopbackLiteral(host: String): Boolean {
        val ipv4Match = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""").matchEntire(host)
            ?: return false
        val octets = ipv4Match.groupValues.drop(1).mapNotNull { it.toIntOrNull() }
        if (octets.size != 4 || octets.any { it !in 0..255 }) return true
        val (a, b) = octets[0] to octets[1]
        return a == 10 ||
            a == 127 ||
            a == 0 ||
            (a == 169 && b == 254) ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168)
    }

    suspend fun sendInstantStockAlert(
        webhookUrl: String,
        recipientEmail: String,
        whatsappPhone: String = SettingsPrefs.DEFAULT_ALERT_WHATSAPP,
        shopName: String,
        action: String = "set_stock",
        triggeredBrand: String,
        triggeredPart: String,
        soldQty: Long = 0L,
        triggeredQty: Long,
        lowLimit: Long,
        updatedBy: String,
        allLowOrEmpty: List<StockItem>
    ): Result<String> {
        val stockStatus = StockStatus.compute(triggeredQty, lowLimit).label
        val label = if (triggeredBrand.equals("Thennaval", ignoreCase = true) || triggeredBrand.isBlank()) {
            triggeredPart.trim()
        } else {
            "$triggeredBrand $triggeredPart".trim()
        }
        val subject = "URGENT $stockStatus: $label (Stock $triggeredQty / Min $lowLimit)"
        val title = "$shopName - Immediate $stockStatus Alert"

        val headers = listOf("Item", "Qty", "Min", "Status", "Restock")
        val rows = allLowOrEmpty.map { item ->
            val itemDisplay = if (item.brand.equals("Thennaval", ignoreCase = true) || item.brand.isBlank()) {
                item.partName
            } else {
                "${item.brand} ${item.partName}".trim()
            }
            val restock = (item.lowStockLimit * 2 - item.quantity).coerceAtLeast(1L)
            listOf(
                itemDisplay.ifBlank { "-" },
                item.quantity.toString(),
                item.lowStockLimit.toString(),
                if (item.quantity <= 0L) "EMPTY STOCK" else "LOW STOCK",
                "+$restock"
            )
        }.ifEmpty {
            listOf(
                listOf(
                    label,
                    triggeredQty.toString(),
                    lowLimit.toString(),
                    if (triggeredQty <= 0L) "EMPTY STOCK" else "LOW STOCK",
                    "+${(lowLimit * 2 - triggeredQty).coerceAtLeast(1L)}"
                )
            )
        }
        val footer = listOf(
            "Action: $action | Item: $label | Sold Qty: $soldQty | Stock: $triggeredQty | Min Stock: $lowLimit",
            "Updated By: $updatedBy"
        )
        val textBody = buildPlainTable(title, headers, rows, footer)

        val extraFields = mapOf(
            "action" to action,
            "email" to recipientEmail.trim(),
            "phone" to whatsappPhone.trim().ifBlank { SettingsPrefs.DEFAULT_ALERT_WHATSAPP },
            "item" to label,
            "soldQty" to soldQty,
            "stock" to triggeredQty,
            "minStock" to lowLimit
        )

        return postEmailPayload(
            webhookUrl = webhookUrl,
            recipientEmail = recipientEmail,
            type = "instant_stock_alert",
            subject = subject,
            title = title,
            headers = headers,
            rows = rows,
            footerLines = footer,
            textBody = textBody,
            extraFields = extraFields
        )
    }

    suspend fun sendWeeklyReportEmail(
        webhookUrl: String,
        recipientEmail: String,
        whatsappPhone: String = SettingsPrefs.DEFAULT_ALERT_WHATSAPP,
        shopName: String,
        orders: List<ShopOrder>,
        collectedPayments: Double,
        lowStockCount: Int,
        emptyStockCount: Int
    ): Result<String> {
        val ist = TimeZone.getTimeZone("Asia/Kolkata")
        val fmt = SimpleDateFormat("dd-MMM-yy", Locale.ENGLISH).apply { timeZone = ist }
        val headers = listOf("Shop / Customer", "Date", "Payment")

        var totalSales = 0.0
        var pendingBalance = 0.0
        var completed = 0
        var pending = 0
        var returned = 0

        val rows = orders.map { order ->
            totalSales += order.finalAmount
            pendingBalance += order.balanceAmount
            val paymentLabel = when (order.orderStatus) {
                "Completed" -> {
                    completed++
                    "Completed"
                }
                "Returned" -> {
                    returned++
                    "Return"
                }
                else -> {
                    pending++
                    "Pending"
                }
            }
            val dateMillis = order.createdAt?.let { it.seconds * 1000L } ?: System.currentTimeMillis()
            listOf(order.customerName.ifBlank { "Customer" }, fmt.format(Date(dateMillis)), paymentLabel)
        }.ifEmpty {
            listOf(listOf("No orders this week", fmt.format(Date()), "-"))
        }

        val title = "$shopName - Weekly Status (Monday 8:00 AM IST)"
        val subject = "$shopName - Weekly Status Report"
        val footer = listOf(
            "Weekly Sales: ₹${"%.2f".format(totalSales)} | Collected: ₹${"%.2f".format(collectedPayments)} | Pending: ₹${"%.2f".format(pendingBalance)}",
            "Completed: $completed | Pending: $pending | Return: $returned",
            "Stock Alerts -> Low: $lowStockCount | Empty: $emptyStockCount"
        )
        val textBody = buildPlainTable(title, headers, rows, footer)

        val extraFields = mapOf(
            "action" to "weekly_report",
            "email" to recipientEmail.trim(),
            "phone" to whatsappPhone.trim().ifBlank { SettingsPrefs.DEFAULT_ALERT_WHATSAPP }
        )

        return postEmailPayload(
            webhookUrl = webhookUrl,
            recipientEmail = recipientEmail,
            type = "weekly_report",
            subject = subject,
            title = title,
            headers = headers,
            rows = rows,
            footerLines = footer,
            textBody = textBody,
            extraFields = extraFields
        )
    }

    suspend fun sendTestEmail(
        webhookUrl: String,
        recipientEmail: String,
        whatsappPhone: String = SettingsPrefs.DEFAULT_ALERT_WHATSAPP,
        shopName: String
    ): Result<String> {
        val headers = listOf("Check", "Schedule", "Status")
        val rows = listOf(
            listOf("Low & Empty Stock", "Immediate", "Enabled"),
            listOf("Weekly Report", "Monday 8:00 AM IST", "Enabled")
        )
        val footer = listOf("Alert webhook verified for $shopName (@2026 thennaval powered by suwa).")
        val title = "$shopName - Email & WhatsApp Alert Test"
        val textBody = buildPlainTable(title, headers, rows, footer)

        val extraFields = mapOf(
            "action" to "manual_check",
            "email" to recipientEmail.trim(),
            "phone" to whatsappPhone.trim().ifBlank { SettingsPrefs.DEFAULT_ALERT_WHATSAPP }
        )

        return postEmailPayload(
            webhookUrl = webhookUrl,
            recipientEmail = recipientEmail,
            type = "manual_check",
            subject = "$shopName - Test Alert Connected",
            title = title,
            headers = headers,
            rows = rows,
            footerLines = footer,
            textBody = textBody,
            extraFields = extraFields
        )
    }

    private suspend fun postEmailPayload(
        webhookUrl: String,
        recipientEmail: String,
        type: String,
        subject: String,
        title: String,
        headers: List<String>,
        rows: List<List<String>>,
        footerLines: List<String>,
        textBody: String,
        extraFields: Map<String, Any> = emptyMap()
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = webhookUrl.trim()
            if (!isSafeWebhookUrl(cleanUrl)) {
                return@withContext Result.failure(
                    IllegalArgumentException("Paste a valid HTTPS Google Apps Script Web App URL in Settings.")
                )
            }
            val parsedHost = URL(cleanUrl).host
            val resolved = InetAddress.getAllByName(parsedHost)
            if (resolved.any { it.isLoopbackAddress || it.isSiteLocalAddress || it.isLinkLocalAddress }) {
                return@withContext Result.failure(
                    IllegalArgumentException("Private or local webhook hosts are not allowed.")
                )
            }
            val cleanRecipient = recipientEmail.trim()
            if (cleanRecipient.isBlank()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Alert Email is required in Settings.")
                )
            }

            val json = JSONObject().apply {
                put("type", type)
                put("to", cleanRecipient)
                put("subject", subject)
                put("title", title)
                put("headers", JSONArray(headers))
                put("rows", JSONArray(rows.map { JSONArray(it) }))
                put("footerLines", JSONArray(footerLines))
                put("textBody", textBody)
                extraFields.forEach { (k, v) -> put(k, v) }
            }

            val request = Request.Builder()
                .url(cleanUrl)
                .post(json.toString().toRequestBody(JSON_MEDIA))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Webhook returned HTTP ${response.code}")
                    )
                }
                if (body.contains("\"ok\":false")) {
                    return@withContext Result.failure(
                        IllegalStateException("Apps Script error: $body")
                    )
                }
            }
            Result.success(textBody)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Alert webhook dispatch failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun buildPlainTable(
        title: String,
        headers: List<String>,
        rows: List<List<String>>,
        footerLines: List<String>
    ): String {
        val sb = StringBuilder()
        sb.appendLine(title)
        sb.appendLine(headers.joinToString(" | "))
        rows.forEach { row ->
            sb.appendLine(row.joinToString(" | "))
        }
        footerLines.forEach { sb.appendLine(it) }
        return sb.toString().trim().take(1900)
    }
}
