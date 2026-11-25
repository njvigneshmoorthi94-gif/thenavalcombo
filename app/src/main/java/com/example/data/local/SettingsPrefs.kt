package com.example.data.local

import android.content.Context
import com.example.data.model.ShopSettings

object SettingsPrefs {
    private const val PREFS_NAME = "thennaval_shop_settings"
    private const val KEY_WEBHOOK_URL = "email_webhook_url"
    private const val KEY_EMAIL = "alert_email"
    private const val KEY_WHATSAPP = "alert_whatsapp"
    private const val KEY_LOW_LIMIT = "low_stock_limit"
    private const val KEY_SHOP_NAME = "shop_name"

    const val DEFAULT_ALERT_EMAIL = "mechmoorthi007@gmail.com"
    const val DEFAULT_ALERT_WHATSAPP = "918526820655"
    const val DEFAULT_LOW_LIMIT = 5L

    fun load(context: Context): ShopSettings {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return ShopSettings(
            shopName = prefs.getString(KEY_SHOP_NAME, "Thennaval Combo").orEmpty().ifBlank { "Thennaval Combo" },
            email = prefs.getString(KEY_EMAIL, DEFAULT_ALERT_EMAIL).orEmpty().ifBlank { DEFAULT_ALERT_EMAIL },
            whatsapp = prefs.getString(KEY_WHATSAPP, DEFAULT_ALERT_WHATSAPP).orEmpty().ifBlank { DEFAULT_ALERT_WHATSAPP },
            emailWebhookUrl = prefs.getString(KEY_WEBHOOK_URL, "").orEmpty(),
            lowStockLimit = prefs.getLong(KEY_LOW_LIMIT, DEFAULT_LOW_LIMIT).coerceAtLeast(0L)
        )
    }

    fun save(context: Context, settings: ShopSettings) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_SHOP_NAME, settings.shopName.ifBlank { "Thennaval Combo" })
            .putString(KEY_EMAIL, settings.email.trim().ifBlank { DEFAULT_ALERT_EMAIL })
            .putString(KEY_WHATSAPP, settings.whatsapp.trim().ifBlank { DEFAULT_ALERT_WHATSAPP })
            .putString(KEY_WEBHOOK_URL, settings.emailWebhookUrl.trim())
            .putLong(KEY_LOW_LIMIT, settings.lowStockLimit.coerceAtLeast(0L))
            .apply()
    }
}
