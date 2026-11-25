package com.example

import com.example.data.firebase.ShopRepository
import com.example.data.firebase.StaffAuthRepository
import com.example.data.firebase.StaffCredentialHasher
import com.example.data.model.DateRangeFilter
import com.example.data.model.OrderLineItem
import com.example.data.model.PaymentRecord
import com.example.data.model.ReturnRecord
import com.example.data.model.ShopOrder
import com.example.data.model.StockItem
import com.example.data.model.StockStatus
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.RoleAccessOutcome
import com.example.ui.viewmodel.ShopViewModel
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopUnitTests {

    @Test
    fun `stock status auto detection works for in stock, low stock, and empty`() {
        assertEquals(StockStatus.EMPTY_STOCK, StockStatus.compute(0L, 3L))
        assertEquals(StockStatus.LOW_STOCK, StockStatus.compute(3L, 3L))
        assertEquals(StockStatus.LOW_STOCK, StockStatus.compute(1L, 3L))
        assertEquals(StockStatus.IN_STOCK, StockStatus.compute(4L, 3L))
    }

    @Test
    fun `metrics compute sales, balances, valuation, and profit only when purchase cost exists`() {
        val now = Timestamp.now()
        val stocks = listOf(
            StockItem(id = "s1", brand = "Vivo", partName = "Display", unitPrice = 2000.0, quantity = 5L, lowStockLimit = 2L),
            StockItem(id = "s2", brand = "Oppo", partName = "Battery", unitPrice = 900.0, quantity = 0L, lowStockLimit = 2L)
        )
        val ordersNoCost = listOf(
            ShopOrder(
                id = "o1",
                items = listOf(OrderLineItem(stockId = "s1", quantity = 1L, unitPrice = 2000.0, purchaseCost = 0.0, lineTotal = 2000.0)),
                finalAmount = 2000.0,
                paidAmount = 1200.0,
                balanceAmount = 800.0,
                orderStatus = "Partial Payment",
                createdAt = now
            )
        )
        val payments = listOf(PaymentRecord(id = "p1", orderId = "o1", amount = 1200.0, paymentDate = now, createdAt = now))
        val returns = emptyList<ReturnRecord>()

        val m1 = ShopViewModel.computeMetrics(stocks, ordersNoCost, payments, returns, DateRangeFilter.TODAY)
        assertEquals(2000.0, m1.totalSales, 0.01)
        assertEquals(1200.0, m1.collectedPayments, 0.01)
        assertEquals(800.0, m1.pendingBalance, 0.01)
        assertEquals(10000.0, m1.stockValuation, 0.01)
        assertEquals(1, m1.emptyStockCount)
        assertNull(m1.totalProfit)

        val ordersWithCost = listOf(
            ShopOrder(
                id = "o2",
                items = listOf(OrderLineItem(stockId = "s1", quantity = 2L, unitPrice = 2000.0, purchaseCost = 1400.0, lineTotal = 4000.0)),
                finalAmount = 4000.0,
                paidAmount = 4000.0,
                balanceAmount = 0.0,
                orderStatus = "Completed",
                createdAt = now
            )
        )
        val m2 = ShopViewModel.computeMetrics(stocks, ordersWithCost, payments, returns, DateRangeFilter.TODAY)
        assertEquals(1200.0, m2.totalProfit ?: 0.0, 0.01)
    }

    @Test
    fun `role verification enforces active owner and labour rules`() {
        val activeOwner = UserProfile(userId = "u1", role = "owner", active = true, name = "Owner")
        val activeLabour = UserProfile(userId = "u2", role = "labour", active = true, name = "Labour")
        val inactiveLabour = UserProfile(userId = "u3", role = "labour", active = false, name = "Pending")

        assertTrue(AuthViewModel.evaluateProfileAccess(activeOwner) is RoleAccessOutcome.Verified)
        assertTrue(AuthViewModel.evaluateProfileAccess(activeLabour) is RoleAccessOutcome.Verified)
        assertTrue(AuthViewModel.evaluateProfileAccess(inactiveLabour) is RoleAccessOutcome.PendingApproval)
    }

    @Test
    fun `staff credential hasher verifies matching passcode and rejects wrong passcode`() {
        val salt = StaffCredentialHasher.generateSalt()
        val hash = StaffCredentialHasher.hashPassword("Shop@2026", salt)
        assertTrue(StaffCredentialHasher.verifyPassword("Shop@2026", salt, hash))
        assertFalse(StaffCredentialHasher.verifyPassword("wrong", salt, hash))
        assertEquals("admin", StaffAuthRepository.sanitizeUsername(" Admin! "))
    }

    @Test
    fun `bootstrapped owner emails match security rules`() {
        assertTrue(ShopRepository.BOOTSTRAPPED_OWNER_EMAILS.contains("njvigneshmoorthi94@gmail.com"))
        assertTrue(ShopRepository.BOOTSTRAPPED_OWNER_EMAILS.contains("mechmoorthi007@gmail.com"))
    }

    @Test
    fun `free email dispatcher validates public https webhook urls and formats tables`() {
        assertTrue(
            com.example.data.firebase.FreeEmailDispatcher.isSafeWebhookUrl(
                "https://script.google.com/macros/s/abc123/exec"
            )
        )
        assertFalse(
            com.example.data.firebase.FreeEmailDispatcher.isSafeWebhookUrl(
                "http://script.google.com/macros/s/abc123/exec"
            )
        )
        assertFalse(
            com.example.data.firebase.FreeEmailDispatcher.isSafeWebhookUrl(
                "https://localhost/hook"
            )
        )
        assertFalse(
            com.example.data.firebase.FreeEmailDispatcher.isSafeWebhookUrl(
                "https://192.168.1.50/hook"
            )
        )
        val table = com.example.data.firebase.FreeEmailDispatcher.buildPlainTable(
            title = "Weekly Report",
            headers = listOf("Shop", "Date", "Status"),
            rows = listOf(listOf("Ravi", "25-Nov-26", "Completed")),
            footerLines = listOf("Low: 2 | Empty: 1")
        )
        assertTrue(table.contains("Weekly Report"))
        assertTrue(table.contains("Ravi | 25-Nov-26 | Completed"))
        assertTrue(table.contains("Low: 2 | Empty: 1"))
        assertFalse(
            com.example.data.firebase.FreeEmailDispatcher.isSafeWebhookUrl(
                "PASTE_YOUR_GOOGLE_APPS_SCRIPT_WEB_APP_URL"
            )
        )
    }

    @Test
    fun `shop settings defaults prefill email whatsapp and min stock threshold`() {
        val defaults = com.example.data.model.ShopSettings()
        assertEquals("mechmoorthi007@gmail.com", defaults.email)
        assertEquals("918526820655", defaults.whatsapp)
        assertEquals(5L, defaults.lowStockLimit)
    }
}
