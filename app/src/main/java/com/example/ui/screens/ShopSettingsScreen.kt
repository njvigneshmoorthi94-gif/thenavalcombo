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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Store
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.NotificationLog
import com.example.data.model.ShopSettings
import com.example.data.model.formatIstDateTime
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusBadge
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavyCard
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ShopSettingsScreen(
    settings: ShopSettings?,
    notifications: List<NotificationLog>,
    isSubmitting: Boolean,
    onSaveSettings: (ShopSettings) -> Unit,
    onSendTestEmail: (ShopSettings) -> Unit,
    onSendWeeklyReportNow: (ShopSettings) -> Unit
) {
    val base = settings ?: ShopSettings()
    var shopName by remember(base) { mutableStateOf(base.shopName) }
    var address by remember(base) { mutableStateOf(base.address) }
    var mobile by remember(base) { mutableStateOf(base.mobile) }
    var ownerName by remember(base) { mutableStateOf(base.ownerName) }
    var email by remember(base) { mutableStateOf(base.email) }
    var whatsapp by remember(base) { mutableStateOf(base.whatsapp) }
    var emailWebhookUrl by remember(base) { mutableStateOf(base.emailWebhookUrl) }
    var lowLimitText by remember(base) { mutableStateOf(base.lowStockLimit.toString()) }
    var morningAlert by remember(base) { mutableStateOf(base.dailyMorningAlert) }
    var nightAlert by remember(base) { mutableStateOf(base.dailyNightAlert) }
    var weeklyReport by remember(base) { mutableStateOf(base.weeklyEmailReport) }

    fun currentFormSettings(): ShopSettings = base.copy(
        shopName = shopName,
        address = address,
        mobile = mobile,
        ownerName = ownerName,
        email = email,
        whatsapp = whatsapp,
        emailWebhookUrl = emailWebhookUrl.trim(),
        lowStockLimit = (lowLimitText.toLongOrNull() ?: 5L).coerceAtLeast(0L),
        dailyMorningAlert = morningAlert,
        dailyNightAlert = nightAlert,
        weeklyEmailReport = weeklyReport
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Store, contentDescription = null, tint = AccentPurple)
                        Text("Shop Profile & Contact", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    }
                    OutlinedTextField(
                        value = shopName,
                        onValueChange = { shopName = it },
                        label = { Text("Shop Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("shop_name_input")
                    )
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Shop Address") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = mobile,
                        onValueChange = { mobile = it },
                        label = { Text("Shop Mobile Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = ownerName,
                        onValueChange = { ownerName = it },
                        label = { Text("Owner Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("owner_name_input")
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Owner Email Address") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("owner_email_input")
                    )
                    OutlinedTextField(
                        value = whatsapp,
                        onValueChange = { whatsapp = it },
                        label = { Text("WhatsApp Number (with country code, e.g. +91...)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = emailWebhookUrl,
                        onValueChange = { emailWebhookUrl = it },
                        label = { Text("Google Apps Script Web App URL") },
                        placeholder = { Text("PASTE_YOUR_GOOGLE_APPS_SCRIPT_WEB_APP_URL") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("email_webhook_url_input")
                    )
                    OutlinedTextField(
                        value = lowLimitText,
                        onValueChange = { lowLimitText = it },
                        label = { Text("Default Minimum Stock Threshold") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = AccentBlue)
                        Text("Automatic Email & WhatsApp Alerts (Asia/Kolkata)", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    }
                    NotificationToggleRow(
                        title = "Daily Morning Stock Alert (9:00 AM IST)",
                        subtitle = "Optional scheduled Cloud Function morning summary",
                        checked = morningAlert,
                        onCheckedChange = { morningAlert = it }
                    )
                    NotificationToggleRow(
                        title = "Daily Night Stock Alert (9:00 PM IST)",
                        subtitle = "Optional scheduled Cloud Function restock checklist",
                        checked = nightAlert,
                        onCheckedChange = { nightAlert = it }
                    )
                    NotificationToggleRow(
                        title = "Weekly Sales & Stock Report (Monday 8:00 AM IST)",
                        subtitle = "Auto-sends weekly orders, payments & low stock every Monday after 8:00 AM IST",
                        checked = weeklyReport,
                        onCheckedChange = { weeklyReport = it }
                    )

                    Button(
                        onClick = { onSaveSettings(currentFormSettings()) },
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_shop_settings_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
                    ) {
                        Icon(Icons.Rounded.Save, contentDescription = null)
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Text("Save Shop Settings")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onSendTestEmail(currentFormSettings()) },
                            enabled = !isSubmitting,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("send_test_email_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                        ) {
                            Text("Send Test Email", maxLines = 1)
                        }
                        Button(
                            onClick = { onSendWeeklyReportNow(currentFormSettings()) },
                            enabled = !isSubmitting,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("send_weekly_report_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = NavyBorder)
                        ) {
                            Text("Send Weekly Report Now", maxLines = 1)
                        }
                    }
                }
            }
        }

        // 100% Free Automatic Email Setup Card (Google Apps Script — No Blaze Plan / No Card)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = NavyCard),
                border = BorderStroke(1.dp, StatusAmber.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("100% Free Automatic Gmail & WhatsApp Setup (@2026 thennaval powered by suwa)", style = MaterialTheme.typography.titleMedium, color = StatusAmber)
                    Text(
                        "Low & Empty Stock alerts POST immediately when stock <= minStock or stock <= 0. Monday 8:00 AM IST weekly reports auto-send automatically.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        "1. Open https://script.google.com on Chrome with your Gmail\n" +
                            "2. New Project -> paste free-email-script/Code.gs -> Save\n" +
                            "3. Deploy -> New deployment -> Web app (Execute as: Me, Access: Anyone)\n" +
                            "4. Paste the /exec URL above -> tap Save Shop Settings -> Send Test Email",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                        color = AccentBlue
                    )
                }
            }
        }

        item {
            SectionHeader(title = "Notification Dispatch Logs")
        }

        if (notifications.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.NotificationsActive,
                    title = "No Dispatches Logged Yet",
                    description = "Instant Low/Empty Stock alerts and Monday 8:00 AM IST weekly email delivery statuses appear here automatically."
                )
            }
        } else {
            items(notifications, key = { it.id }) { log ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyCard),
                    border = BorderStroke(1.dp, NavyBorder.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${log.channel.uppercase()} • ${log.type}", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            StatusBadge(label = log.status)
                        }
                        Text(log.message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Text(
                            "To: ${log.recipient.ifBlank { "Unconfigured" }} • ${formatIstDateTime(log.createdAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun NotificationToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
