package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.local.SettingsPrefs
import com.example.data.model.NotificationLog
import com.example.data.model.OrderLineItem
import com.example.data.model.PaymentRecord
import com.example.data.model.ReturnRecord
import com.example.data.model.ShopOrder
import com.example.data.model.ShopSettings
import com.example.data.model.StockItem
import com.example.data.model.StockStatus
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class StockAlertOutcome(
    val alertTriggered: Boolean = false,
    val alertSent: Boolean = false,
    val bannerMessage: String? = null,
    val alertError: String? = null
)

class ShopRepository(
    private val db: FirebaseFirestore,
    private val appContext: Context? = null
) {

    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        appContext = context.applicationContext
    )

    private val auth = Firebase.auth

    fun loadLocalSettings(): ShopSettings =
        appContext?.let { SettingsPrefs.load(it) } ?: ShopSettings()

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun <T> Flow<T>.guardFirestoreStream(uid: String, path: String): Flow<T> =
        this.catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val exceptionToLog = firestoreError ?: (error as? Exception)
            if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                handleFirestoreError(exceptionToLog, OperationType.LIST, path)
            }
            throw error
        }

    suspend fun ensureUserProfile(
        firebaseUser: FirebaseUser,
        preferredRole: UserRole
    ): Result<UserProfile> {
        val docPath = "users/${firebaseUser.uid}"
        return try {
            val uid = requireUserId()
            val email = firebaseUser.email.orEmpty().trim()
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()

            if (snap.exists()) {
                val profile = snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: throw IllegalStateException("Failed to deserialize user profile.")
                val isBootstrappedOwner = firebaseUser.isEmailVerified && BOOTSTRAPPED_OWNER_EMAILS.contains(email.lowercase(Locale.ROOT))
                if (isBootstrappedOwner && (profile.userRole != UserRole.OWNER || !profile.active)) {
                    docRef.update(
                        mapOf(
                            "role" to UserRole.OWNER.wireValue,
                            "active" to true,
                            "email" to email.take(120),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    val refreshed = docRef.get().await()
                        .toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?: profile.copy(role = UserRole.OWNER.wireValue, active = true, email = email)
                    return Result.success(refreshed)
                }
                return Result.success(profile)
            }

            val isBootstrappedOwner = firebaseUser.isEmailVerified && BOOTSTRAPPED_OWNER_EMAILS.contains(email.lowercase(Locale.ROOT))
            val assignedRole = if (isBootstrappedOwner) UserRole.OWNER else UserRole.LABOUR
            val active = isBootstrappedOwner
            val displayName = firebaseUser.displayName?.trim()?.takeIf { it.isNotBlank() }
                ?: email.substringBefore("@").ifBlank { "Staff Member" }
            val usernameSeed = email.substringBefore("@")
                .lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9._-]"), "")
                .ifBlank { if (isBootstrappedOwner) "admin" else "labour" }
                .take(40)
            val safeUsername = if (usernameSeed.length >= 2) usernameSeed else "u_$usernameSeed"

            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "username" to safeUsername,
                "role" to assignedRole.wireValue,
                "name" to displayName.take(80),
                "active" to active,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (email.isNotBlank()) {
                payload["email"] = email.take(120)
            }

            docRef.set(payload).await()
            val created = docRef.get().await()
                .toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: UserProfile(
                    id = uid,
                    userId = uid,
                    username = safeUsername,
                    role = assignedRole.wireValue,
                    name = displayName,
                    email = email,
                    active = active
                )
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        if (userId.isBlank()) {
            emit(null)
            return@flow
        }
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snap ->
                    if (!snap.exists()) null
                    else snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeAllUsers(): Flow<List<UserProfile>> = flow {
        val uid = requireUserId()
        val path = "users"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.name.lowercase(Locale.ROOT) }
                }
                .guardFirestoreStream(uid, path)
        )
    }

    fun observeStocks(): Flow<List<StockItem>> = flow {
        val uid = requireUserId()
        val path = "stocks"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(StockItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
                }
                .guardFirestoreStream(uid, path)
        )
    }

    fun observeOrders(): Flow<List<ShopOrder>> = flow {
        val uid = requireUserId()
        val path = "orders"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(ShopOrder::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .guardFirestoreStream(uid, path)
        )
    }

    fun observePayments(): Flow<List<PaymentRecord>> = flow {
        val uid = requireUserId()
        val path = "payments"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(PaymentRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.paymentDate?.seconds ?: it.createdAt?.seconds ?: 0L }
                }
                .guardFirestoreStream(uid, path)
        )
    }

    fun observeReturns(): Flow<List<ReturnRecord>> = flow {
        val uid = requireUserId()
        val path = "returns"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(ReturnRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.returnDate?.seconds ?: it.createdAt?.seconds ?: 0L }
                }
                .guardFirestoreStream(uid, path)
        )
    }

    fun observeShopSettings(): Flow<ShopSettings?> = flow {
        val uid = requireUserId()
        val path = "shops/main_shop"
        emitAll(
            db.collection("shops").document("main_shop")
                .snapshots()
                .map { snap ->
                    if (!snap.exists()) null
                    else snap.toObject(ShopSettings::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .guardFirestoreStream(uid, path)
        )
    }

    fun observeNotifications(): Flow<List<NotificationLog>> = flow {
        val uid = requireUserId()
        val path = "notifications"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(NotificationLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .guardFirestoreStream(uid, path)
        )
    }

    suspend fun saveStock(
        stockId: String?,
        brand: String,
        partName: String,
        category: String,
        unitPrice: Double,
        purchaseCost: Double?,
        quantity: Long,
        lowStockLimit: Long,
        actorName: String,
        actorUid: String
    ): Result<StockAlertOutcome> {
        var docPath = "stocks/${stockId ?: "new"}"
        return try {
            val uid = requireUserId()
            val cleanBrand = brand.trim()
            val cleanPart = partName.trim()
            val cleanCat = category.trim().ifBlank { "General" }
            require(cleanBrand.length in 1..60) { "Brand must be 1–60 characters." }
            require(cleanPart.length in 1..80) { "Item name must be 1–80 characters." }
            require(cleanCat.length in 1..60) { "Category must be 1–60 characters." }
            require(unitPrice >= 0.0) { "Unit price cannot be negative." }
            require(purchaseCost == null || purchaseCost >= 0.0) { "Purchase cost cannot be negative." }
            require(quantity >= 0L) { "Quantity cannot be negative." }
            require(lowStockLimit >= 0L) { "Low stock limit cannot be negative." }

            val status = StockStatus.compute(quantity, lowStockLimit).label
            val cleanActor = actorName.trim().ifBlank { "Staff" }.take(80)
            val safeUid = uid.take(128)

            if (stockId == null) {
                val docRef = db.collection("stocks").document()
                docPath = docRef.path
                val payload = mutableMapOf<String, Any>(
                    "brand" to cleanBrand,
                    "partName" to cleanPart,
                    "category" to cleanCat,
                    "unitPrice" to unitPrice,
                    "quantity" to quantity,
                    "lowStockLimit" to lowStockLimit,
                    "status" to status,
                    "updatedBy" to cleanActor,
                    "updatedByUid" to safeUid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (purchaseCost != null) {
                    payload["purchaseCost"] = purchaseCost
                }
                docRef.set(payload).await()
            } else {
                require(stockId.isNotBlank()) { "Stock ID cannot be blank." }
                val docRef = db.collection("stocks").document(stockId)
                docPath = docRef.path
                val updates = mutableMapOf<String, Any>(
                    "brand" to cleanBrand,
                    "partName" to cleanPart,
                    "category" to cleanCat,
                    "unitPrice" to unitPrice,
                    "quantity" to quantity,
                    "lowStockLimit" to lowStockLimit,
                    "status" to status,
                    "updatedBy" to cleanActor,
                    "updatedByUid" to safeUid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (purchaseCost != null) {
                    updates["purchaseCost"] = purchaseCost
                }
                docRef.update(updates).await()
            }
            val alertOutcome = if (quantity <= lowStockLimit) {
                dispatchInstantStockAlert(
                    action = "set_stock",
                    triggeredBrand = cleanBrand,
                    triggeredPart = cleanPart,
                    soldQty = 0L,
                    triggeredQty = quantity,
                    lowLimit = lowStockLimit,
                    updatedBy = cleanActor
                )
            } else {
                StockAlertOutcome()
            }
            Result.success(alertOutcome)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun adjustStockQuantity(
        stock: StockItem,
        newQuantity: Long,
        actorName: String,
        actorUid: String,
        action: String = if (newQuantity < stock.quantity) "sale" else "set_stock",
        soldQty: Long = (stock.quantity - newQuantity).coerceAtLeast(0L)
    ): Result<StockAlertOutcome> {
        val docPath = "stocks/${stock.id}"
        return try {
            val uid = requireUserId()
            require(stock.id.isNotBlank()) { "Stock ID missing." }
            require(newQuantity >= 0L) { "Quantity cannot be negative." }
            val nextStatus = StockStatus.compute(newQuantity, stock.lowStockLimit).label
            val cleanActor = actorName.trim().ifBlank { "Staff" }.take(80)
            val safeUid = uid.take(128)
            db.collection("stocks").document(stock.id).update(
                mapOf(
                    "quantity" to newQuantity,
                    "status" to nextStatus,
                    "updatedBy" to cleanActor,
                    "updatedByUid" to safeUid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            val alertOutcome = if (newQuantity <= stock.lowStockLimit) {
                dispatchInstantStockAlert(
                    action = action,
                    triggeredBrand = stock.brand,
                    triggeredPart = stock.partName,
                    soldQty = soldQty,
                    triggeredQty = newQuantity,
                    lowLimit = stock.lowStockLimit,
                    updatedBy = cleanActor
                )
            } else {
                StockAlertOutcome()
            }
            Result.success(alertOutcome)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun loadSampleItems(
        actorName: String,
        defaultMinStock: Long
    ): Result<Int> {
        return try {
            val uid = requireUserId()
            val cleanActor = actorName.trim().ifBlank { "Staff" }.take(80)
            val safeUid = uid.take(128)
            val existing = db.collection("stocks").get().await()
                .toObjects(StockItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .map { it.partName.lowercase(Locale.ROOT) }
                .toSet()

            val minLimit = defaultMinStock.coerceAtLeast(1L)
            val samples = listOf(
                Triple("Basmati Rice 25kg", 8L, 1450.0),
                Triple("Sunflower Oil 1L", 3L, 165.0),
                Triple("Sugar 1kg", 0L, 48.0)
            )
            var added = 0
            for ((name, qty, price) in samples) {
                if (existing.contains(name.lowercase(Locale.ROOT))) continue
                val status = StockStatus.compute(qty, minLimit).label
                db.collection("stocks").document().set(
                    mapOf(
                        "brand" to "Thennaval",
                        "partName" to name,
                        "category" to "Grocery",
                        "unitPrice" to price,
                        "quantity" to qty,
                        "lowStockLimit" to minLimit,
                        "status" to status,
                        "updatedBy" to cleanActor,
                        "updatedByUid" to safeUid,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                added++
            }
            Result.success(added)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "stocks")
            Result.failure(e)
        }
    }

    suspend fun deleteStock(stockId: String): Result<Unit> {
        val docPath = "stocks/$stockId"
        return try {
            requireUserId()
            require(stockId.isNotBlank()) { "Stock ID cannot be blank." }
            db.collection("stocks").document(stockId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createOrder(
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
        actorUid: String
    ): Result<StockAlertOutcome> {
        var docPath = "orders"
        return try {
            val uid = requireUserId()
            val cleanCust = customerName.trim()
            val cleanMobile = mobile.trim()
            val cleanLabour = labourName.trim().ifBlank { actorName.trim().ifBlank { "Staff" } }.take(80)
            val cleanActor = actorName.trim().ifBlank { cleanLabour }.take(80)
            val safeUid = uid.take(128)

            require(cleanCust.length in 1..100) { "Customer name must be 1–100 characters." }
            require(cleanMobile.length in 5..20) { "Mobile number must be 5–20 characters." }
            require(selectedStockId.isNotBlank()) { "Please select a stock part." }
            require(quantity >= 1L) { "Quantity must be at least 1." }
            require(discount >= 0.0) { "Discount cannot be negative." }
            require(paidAmount >= 0.0) { "Paid amount cannot be negative." }
            require(paymentMethod in listOf("Cash", "UPI", "Bank Transfer", "Other")) {
                "Unsupported payment method."
            }

            val stockRef = db.collection("stocks").document(selectedStockId)
            val orderRef = db.collection("orders").document()
            val paymentRef = db.collection("payments").document()
            docPath = orderRef.path
            val orderNum = "TC-${(System.currentTimeMillis() % 900000L) + 100000L}"

            val alertCheck = db.runTransaction { tx ->
                // ALL reads BEFORE any writes
                val stockSnap = tx.get(stockRef)
                if (!stockSnap.exists()) {
                    throw IllegalStateException("Selected stock item no longer exists.")
                }

                val availableQty = (stockSnap.get("quantity") as? Number)?.toLong() ?: 0L
                val lowLimit = (stockSnap.get("lowStockLimit") as? Number)?.toLong() ?: 3L
                val brand = (stockSnap.getString("brand") ?: "Part").take(60)
                val partName = (stockSnap.getString("partName") ?: "Item").take(80)
                val basePrice = (stockSnap.get("unitPrice") as? Number)?.toDouble() ?: 0.0
                val purchaseCost = (stockSnap.get("purchaseCost") as? Number)?.toDouble() ?: 0.0

                if (availableQty < quantity) {
                    throw IllegalStateException("Insufficient stock: only $availableQty units left.")
                }

                val effectivePrice = (unitPriceOverride ?: basePrice).coerceAtLeast(0.0)
                val totalAmount = effectivePrice * quantity
                val finalAmount = (totalAmount - discount).coerceAtLeast(0.0)
                val clampedPaid = paidAmount.coerceIn(0.0, finalAmount)
                val balanceAmount = (finalAmount - clampedPaid).coerceAtLeast(0.0)

                val (orderStatus, paymentStatus) = when {
                    balanceAmount <= 0.0 -> "Completed" to "Paid"
                    clampedPaid > 0.0 -> "Partial Payment" to "Partial"
                    else -> "Unpaid" to "Unpaid"
                }

                val remainingQty = availableQty - quantity
                val nextStockStatus = StockStatus.compute(remainingQty, lowLimit).label

                val lineItemMap = mapOf(
                    "stockId" to selectedStockId,
                    "brand" to brand,
                    "partName" to partName,
                    "quantity" to quantity,
                    "unitPrice" to effectivePrice,
                    "purchaseCost" to purchaseCost,
                    "lineTotal" to totalAmount
                )

                val orderPayload = mapOf(
                    "orderNumber" to orderNum,
                    "customerName" to cleanCust,
                    "mobile" to cleanMobile,
                    "items" to listOf(lineItemMap),
                    "brandSummary" to brand.take(80),
                    "partSummary" to partName.take(120),
                    "totalQuantity" to quantity,
                    "totalAmount" to totalAmount,
                    "discount" to discount,
                    "finalAmount" to finalAmount,
                    "paidAmount" to clampedPaid,
                    "balanceAmount" to balanceAmount,
                    "orderStatus" to orderStatus,
                    "paymentStatus" to paymentStatus,
                    "labourName" to cleanLabour,
                    "createdBy" to cleanActor,
                    "createdByUid" to safeUid,
                    "updatedBy" to cleanActor,
                    "updatedByUid" to safeUid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                tx.update(
                    stockRef,
                    mapOf(
                        "quantity" to remainingQty,
                        "status" to nextStockStatus,
                        "updatedBy" to cleanActor,
                        "updatedByUid" to safeUid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.set(orderRef, orderPayload)

                if (clampedPaid > 0.0) {
                    val paymentPayload = mapOf(
                        "orderId" to orderRef.id,
                        "orderNumber" to orderNum,
                        "customerName" to cleanCust,
                        "mobile" to cleanMobile,
                        "amount" to clampedPaid,
                        "paymentMethod" to paymentMethod,
                        "collectedBy" to cleanActor,
                        "collectedByUid" to safeUid,
                        "notes" to "Initial order payment",
                        "paymentDate" to FieldValue.serverTimestamp(),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    tx.set(paymentRef, paymentPayload)
                }
                Pair(brand to partName, remainingQty to lowLimit)
            }.await()

            val (brandPart, qtyLimit) = alertCheck
            val alertOutcome = if (qtyLimit.first <= qtyLimit.second) {
                dispatchInstantStockAlert(
                    action = "sale",
                    triggeredBrand = brandPart.first,
                    triggeredPart = brandPart.second,
                    soldQty = quantity,
                    triggeredQty = qtyLimit.first,
                    lowLimit = qtyLimit.second,
                    updatedBy = cleanActor
                )
            } else {
                StockAlertOutcome()
            }

            Result.success(alertOutcome)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun collectPayment(
        orderId: String,
        amount: Double,
        paymentMethod: String,
        notes: String,
        actorName: String,
        actorUid: String
    ): Result<Unit> {
        var docPath = "orders/$orderId"
        return try {
            val uid = requireUserId()
            require(orderId.isNotBlank()) { "Order ID missing." }
            require(amount > 0.0) { "Payment amount must be greater than zero." }
            require(paymentMethod in listOf("Cash", "UPI", "Bank Transfer", "Other")) {
                "Unsupported payment method."
            }
            val cleanActor = actorName.trim().ifBlank { "Staff" }.take(80)
            val safeUid = uid.take(128)
            val cleanNotes = notes.trim().take(200)

            val orderRef = db.collection("orders").document(orderId)
            val paymentRef = db.collection("payments").document()
            docPath = paymentRef.path

            db.runTransaction { tx ->
                val orderSnap = tx.get(orderRef)
                if (!orderSnap.exists()) {
                    throw IllegalStateException("Order not found.")
                }

                val currentStatus = orderSnap.getString("orderStatus") ?: "Unpaid"
                if (currentStatus == "Returned") {
                    throw IllegalStateException("Cannot collect payment on a returned order.")
                }

                val finalAmount = (orderSnap.get("finalAmount") as? Number)?.toDouble() ?: 0.0
                val currentPaid = (orderSnap.get("paidAmount") as? Number)?.toDouble() ?: 0.0
                val orderNumber = orderSnap.getString("orderNumber") ?: "ORD"
                val customerName = orderSnap.getString("customerName") ?: "Customer"
                val mobile = orderSnap.getString("mobile") ?: ""

                val maxCollectable = (finalAmount - currentPaid).coerceAtLeast(0.0)
                if (maxCollectable <= 0.0) {
                    throw IllegalStateException("Order is already fully paid.")
                }
                val appliedAmount = amount.coerceAtMost(maxCollectable)
                val newPaid = currentPaid + appliedAmount
                val newBalance = (finalAmount - newPaid).coerceAtLeast(0.0)

                val (nextOrderStatus, nextPaymentStatus) = if (newBalance <= 0.0) {
                    "Completed" to "Paid"
                } else {
                    "Partial Payment" to "Partial"
                }

                tx.update(
                    orderRef,
                    mapOf(
                        "paidAmount" to newPaid,
                        "balanceAmount" to newBalance,
                        "orderStatus" to nextOrderStatus,
                        "paymentStatus" to nextPaymentStatus,
                        "updatedBy" to cleanActor,
                        "updatedByUid" to safeUid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                val paymentPayload = mutableMapOf<String, Any>(
                    "orderId" to orderId,
                    "orderNumber" to orderNumber,
                    "customerName" to customerName,
                    "mobile" to mobile,
                    "amount" to appliedAmount,
                    "paymentMethod" to paymentMethod,
                    "collectedBy" to cleanActor,
                    "collectedByUid" to safeUid,
                    "paymentDate" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp()
                )
                if (cleanNotes.isNotBlank()) {
                    paymentPayload["notes"] = cleanNotes
                }
                tx.set(paymentRef, paymentPayload)
                null
            }.await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun processReturn(
        order: ShopOrder,
        returnQuantity: Long,
        reason: String,
        refundAmount: Double,
        returnStatus: String,
        actorName: String,
        actorUid: String
    ): Result<Unit> {
        var docPath = "returns"
        return try {
            val uid = requireUserId()
            require(order.id.isNotBlank()) { "Order ID missing." }
            val lineItem = order.items.firstOrNull()
                ?: throw IllegalStateException("Order has no line item to return.")
            require(returnQuantity >= 1L && returnQuantity <= order.totalQuantity.coerceAtLeast(1L)) {
                "Invalid return quantity."
            }
            val cleanReason = reason.trim()
            require(cleanReason.length in 1..300) { "Return reason is required (max 300 chars)." }
            require(refundAmount >= 0.0) { "Refund amount cannot be negative." }
            require(returnStatus in listOf("Processed", "Pending", "Rejected")) {
                "Invalid return status."
            }

            val cleanActor = actorName.trim().ifBlank { "Staff" }.take(80)
            val safeUid = uid.take(128)

            val orderRef = db.collection("orders").document(order.id)
            val stockRef = db.collection("stocks").document(lineItem.stockId)
            val returnRef = db.collection("returns").document()
            docPath = returnRef.path

            db.runTransaction { tx ->
                // ALL reads BEFORE writes
                val orderSnap = tx.get(orderRef)
                val stockSnap = tx.get(stockRef)
                if (!orderSnap.exists()) {
                    throw IllegalStateException("Order not found.")
                }

                if (returnStatus == "Processed") {
                    if (stockSnap.exists()) {
                        val currentQty = (stockSnap.get("quantity") as? Number)?.toLong() ?: 0L
                        val lowLimit = (stockSnap.get("lowStockLimit") as? Number)?.toLong() ?: 3L
                        val restoredQty = currentQty + returnQuantity
                        val restoredStatus = StockStatus.compute(restoredQty, lowLimit).label
                        tx.update(
                            stockRef,
                            mapOf(
                                "quantity" to restoredQty,
                                "status" to restoredStatus,
                                "updatedBy" to cleanActor,
                                "updatedByUid" to safeUid,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }

                    val existingPaid = (orderSnap.get("paidAmount") as? Number)?.toDouble() ?: 0.0
                    val updatedPaid = (existingPaid - refundAmount).coerceAtLeast(0.0)
                    tx.update(
                        orderRef,
                        mapOf(
                            "paidAmount" to updatedPaid,
                            "balanceAmount" to 0.0,
                            "orderStatus" to "Returned",
                            "paymentStatus" to "Refunded",
                            "updatedBy" to cleanActor,
                            "updatedByUid" to safeUid,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                val productLabel = "${lineItem.brand} - ${lineItem.partName}".take(140)
                val returnPayload = mapOf(
                    "orderId" to order.id,
                    "orderNumber" to order.orderNumber.ifBlank { "ORD" }.take(40),
                    "customerName" to order.customerName.ifBlank { "Customer" }.take(100),
                    "mobile" to order.mobile.take(20),
                    "stockId" to lineItem.stockId,
                    "product" to productLabel,
                    "quantity" to returnQuantity,
                    "reason" to cleanReason,
                    "refundAmount" to refundAmount,
                    "status" to returnStatus,
                    "processedBy" to cleanActor,
                    "processedByUid" to safeUid,
                    "returnDate" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                tx.set(returnRef, returnPayload)
                null
            }.await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveShopSettings(
        settings: ShopSettings,
        actorName: String,
        actorUid: String
    ): Result<Unit> {
        val docPath = "shops/main_shop"
        return try {
            val uid = requireUserId()
            val cleanShopName = settings.shopName.trim()
            val cleanOwnerName = settings.ownerName.trim()
            require(cleanShopName.length in 1..100) { "Shop name is required (max 100 chars)." }
            require(cleanOwnerName.length in 1..80) { "Owner name is required (max 80 chars)." }
            require(settings.lowStockLimit in 0L..10000L) { "Low stock limit must be 0–10000." }

            val cleanActor = actorName.trim().ifBlank { cleanOwnerName }.take(80)
            val safeUid = uid.take(128)
            val docRef = db.collection("shops").document("main_shop")
            val existing = docRef.get().await()

            val cleanWebhook = settings.emailWebhookUrl.trim().take(300)
            if (cleanWebhook.isNotEmpty() && !FreeEmailDispatcher.isSafeWebhookUrl(cleanWebhook)) {
                throw IllegalArgumentException("Webhook URL must start with https:// and point to a public host.")
            }

            val normalizedSettings = settings.copy(
                shopName = cleanShopName,
                ownerName = cleanOwnerName,
                email = settings.email.trim().ifBlank { SettingsPrefs.DEFAULT_ALERT_EMAIL }.take(120),
                whatsapp = settings.whatsapp.trim().ifBlank { SettingsPrefs.DEFAULT_ALERT_WHATSAPP }.take(20),
                emailWebhookUrl = cleanWebhook
            )
            appContext?.let { SettingsPrefs.save(it, normalizedSettings) }

            try {
                if (existing.exists()) {
                    docRef.update(
                        mapOf(
                            "shopName" to cleanShopName,
                            "address" to normalizedSettings.address.trim().take(250),
                            "mobile" to normalizedSettings.mobile.trim().take(20),
                            "ownerName" to cleanOwnerName,
                            "email" to normalizedSettings.email,
                            "whatsapp" to normalizedSettings.whatsapp,
                            "emailWebhookUrl" to cleanWebhook,
                            "lowStockLimit" to normalizedSettings.lowStockLimit,
                            "dailyMorningAlert" to normalizedSettings.dailyMorningAlert,
                            "dailyNightAlert" to normalizedSettings.dailyNightAlert,
                            "weeklyEmailReport" to normalizedSettings.weeklyEmailReport,
                            "updatedBy" to cleanActor,
                            "updatedByUid" to safeUid,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    docRef.set(
                        mapOf(
                            "shopName" to cleanShopName,
                            "address" to normalizedSettings.address.trim().take(250),
                            "mobile" to normalizedSettings.mobile.trim().take(20),
                            "ownerName" to cleanOwnerName,
                            "email" to normalizedSettings.email,
                            "whatsapp" to normalizedSettings.whatsapp,
                            "emailWebhookUrl" to cleanWebhook,
                            "lastWeeklyReportKey" to "",
                            "lowStockLimit" to normalizedSettings.lowStockLimit,
                            "dailyMorningAlert" to normalizedSettings.dailyMorningAlert,
                            "dailyNightAlert" to normalizedSettings.dailyNightAlert,
                            "weeklyEmailReport" to normalizedSettings.weeklyEmailReport,
                            "updatedBy" to cleanActor,
                            "updatedByUid" to safeUid,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            } catch (firestoreErr: FirebaseFirestoreException) {
                if (firestoreErr.code == FirebaseFirestoreException.Code.PERMISSION_DENIED && appContext != null) {
                    android.util.Log.w("ShopRepository", "Saved local settings only (owner required for cloud shops write)")
                } else {
                    throw firestoreErr
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    private fun resolveEffectiveSettings(shop: ShopSettings?): ShopSettings {
        val local = appContext?.let { SettingsPrefs.load(it) } ?: ShopSettings()
        if (shop == null) return local
        return shop.copy(
            shopName = shop.shopName.ifBlank { local.shopName },
            email = shop.email.trim().ifBlank { local.email },
            whatsapp = shop.whatsapp.trim().ifBlank { local.whatsapp },
            emailWebhookUrl = shop.emailWebhookUrl.trim().ifBlank { local.emailWebhookUrl }
        )
    }

    private fun resolveRecipientEmail(shop: ShopSettings?): String {
        val configured = shop?.email?.trim().orEmpty()
        if (configured.isNotBlank()) return configured
        val localEmail = appContext?.let { SettingsPrefs.load(it).email }.orEmpty()
        if (localEmail.isNotBlank()) return localEmail
        return SettingsPrefs.DEFAULT_ALERT_EMAIL
    }

    private suspend fun logNotification(
        type: String,
        channel: String,
        message: String,
        status: String,
        recipient: String
    ) {
        try {
            db.collection("notifications").document().set(
                mapOf(
                    "type" to type,
                    "channel" to channel,
                    "message" to message.ifBlank { type }.take(2000),
                    "status" to status,
                    "recipient" to recipient.take(150),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w(
                "ShopRepository",
                "Notification audit write skipped: ${e.message}"
            )
        }
    }

    private suspend fun dispatchInstantStockAlert(
        action: String,
        triggeredBrand: String,
        triggeredPart: String,
        soldQty: Long,
        triggeredQty: Long,
        lowLimit: Long,
        updatedBy: String
    ): StockAlertOutcome {
        return try {
            val shopSnap = db.collection("shops").document("main_shop").get().await()
            val remoteShop = if (shopSnap.exists()) {
                shopSnap.toObject(ShopSettings::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }
            val shop = resolveEffectiveSettings(remoteShop)
            val recipient = resolveRecipientEmail(shop)
            val whatsappPhone = shop.whatsapp.trim().ifBlank { SettingsPrefs.DEFAULT_ALERT_WHATSAPP }
            val webhook = shop.emailWebhookUrl.trim()
            val shopName = shop.shopName.ifBlank { "Thennaval Combo" }

            val stocksSnap = db.collection("stocks").get().await()
            val lowOrEmpty = stocksSnap.toObjects(StockItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .filter { it.quantity <= it.lowStockLimit }

            if (webhook.isBlank()) {
                val msg = "Low/Empty Stock ($triggeredPart: $triggeredQty). Paste your Apps Script URL in Settings to send Gmail & WhatsApp alerts."
                logNotification(
                    type = "instant_stock_alert",
                    channel = "email",
                    message = msg,
                    status = "requires_backend_config",
                    recipient = recipient
                )
                return StockAlertOutcome(
                    alertTriggered = true,
                    alertSent = false,
                    alertError = msg
                )
            }

            val dispatchResult = FreeEmailDispatcher.sendInstantStockAlert(
                webhookUrl = webhook,
                recipientEmail = recipient,
                whatsappPhone = whatsappPhone,
                shopName = shopName,
                action = action,
                triggeredBrand = triggeredBrand,
                triggeredPart = triggeredPart,
                soldQty = soldQty,
                triggeredQty = triggeredQty,
                lowLimit = lowLimit,
                updatedBy = updatedBy,
                allLowOrEmpty = lowOrEmpty
            )
            var outcome = StockAlertOutcome(alertTriggered = true, alertSent = false)
            dispatchResult.onSuccess { msg ->
                logNotification("instant_stock_alert", "email", msg, "sent", recipient)
                outcome = StockAlertOutcome(
                    alertTriggered = true,
                    alertSent = true,
                    bannerMessage = "Alert sent to Gmail & WhatsApp"
                )
            }.onFailure { err ->
                val errMsg = err.message ?: "Alert webhook error"
                logNotification(
                    type = "instant_stock_alert",
                    channel = "email",
                    message = "Failed: $errMsg",
                    status = "failed",
                    recipient = recipient
                )
                outcome = StockAlertOutcome(
                    alertTriggered = true,
                    alertSent = false,
                    alertError = "Alert failed: $errMsg"
                )
            }
            outcome
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("ShopRepository", "Instant stock alert skipped: ${e.message}")
            StockAlertOutcome(
                alertTriggered = true,
                alertSent = false,
                alertError = e.message ?: "Unable to dispatch stock alert."
            )
        }
    }

    suspend fun sendTestEmail(settings: ShopSettings): Result<Unit> {
        return try {
            requireUserId()
            val effective = resolveEffectiveSettings(settings)
            val recipient = resolveRecipientEmail(effective)
            val phone = effective.whatsapp.trim().ifBlank { SettingsPrefs.DEFAULT_ALERT_WHATSAPP }
            val webhook = effective.emailWebhookUrl.trim()
            val shopName = effective.shopName.ifBlank { "Thennaval Combo" }
            val result = FreeEmailDispatcher.sendTestEmail(webhook, recipient, phone, shopName)
            result.onSuccess { msg ->
                logNotification("manual_check", "email", msg, "sent", recipient)
            }.onFailure { err ->
                logNotification("manual_check", "email", "Test failed: ${err.message}", "failed", recipient)
            }
            result.map { Unit }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun dispatchWeeklyReportNow(
        settings: ShopSettings?,
        orders: List<ShopOrder>,
        payments: List<PaymentRecord>,
        stocks: List<StockItem>,
        actorName: String,
        actorUid: String,
        onlyIfMonday8AmDue: Boolean
    ): Result<Boolean> {
        return try {
            val uid = requireUserId()
            val effective = resolveEffectiveSettings(settings)
            val ist = TimeZone.getTimeZone("Asia/Kolkata")
            val cal = Calendar.getInstance(ist)
            val weekKey = "%04d-W%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.WEEK_OF_YEAR))

            if (onlyIfMonday8AmDue) {
                if (!effective.weeklyEmailReport) return Result.success(false)
                val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                val isAfterMonday8Am = dayOfWeek == Calendar.MONDAY && hour >= 8
                if (!isAfterMonday8Am || effective.lastWeeklyReportKey == weekKey) {
                    return Result.success(false)
                }
            }

            val webhook = effective.emailWebhookUrl.trim()
            val recipient = resolveRecipientEmail(effective)
            val phone = effective.whatsapp.trim().ifBlank { SettingsPrefs.DEFAULT_ALERT_WHATSAPP }
            if (webhook.isBlank()) {
                return if (onlyIfMonday8AmDue) {
                    Result.success(false)
                } else {
                    Result.failure(IllegalStateException("Paste your Google Apps Script Web App URL in Settings first."))
                }
            }

            val weekAgoMillis = System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L
            val weeklyOrders = orders.filter {
                val ms = it.createdAt?.let { ts -> ts.seconds * 1000L } ?: System.currentTimeMillis()
                ms >= weekAgoMillis
            }
            val collected = payments.filter {
                val ms = (it.paymentDate ?: it.createdAt)?.let { ts -> ts.seconds * 1000L } ?: System.currentTimeMillis()
                ms >= weekAgoMillis
            }.sumOf { it.amount }
            val lowCount = stocks.count { it.computedStatus == StockStatus.LOW_STOCK }
            val emptyCount = stocks.count { it.computedStatus == StockStatus.EMPTY_STOCK }
            val shopName = effective.shopName.ifBlank { "Thennaval Combo" }

            val res = FreeEmailDispatcher.sendWeeklyReportEmail(
                webhookUrl = webhook,
                recipientEmail = recipient,
                whatsappPhone = phone,
                shopName = shopName,
                orders = weeklyOrders,
                collectedPayments = collected,
                lowStockCount = lowCount,
                emptyStockCount = emptyCount
            )

            res.onSuccess { msg ->
                logNotification("weekly_report", "email", msg, "sent", recipient)
                val shopRef = db.collection("shops").document("main_shop")
                val shopSnap = shopRef.get().await()
                if (shopSnap.exists()) {
                    shopRef.update(
                        mapOf(
                            "lastWeeklyReportKey" to weekKey,
                            "updatedBy" to actorName.ifBlank { "Owner" }.take(80),
                            "updatedByUid" to uid.take(128),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }.onFailure { err ->
                logNotification("weekly_report", "email", "Weekly failed: ${err.message}", "failed", recipient)
            }
            res.map { true }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUserAccess(
        targetUserId: String,
        role: UserRole,
        active: Boolean,
        name: String,
        username: String
    ): Result<Unit> {
        val docPath = "users/$targetUserId"
        return try {
            requireUserId()
            require(targetUserId.isNotBlank()) { "User ID missing." }
            val cleanName = name.trim().ifBlank { "Staff" }.take(80)
            val cleanUsername = username.trim().lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9._-]"), "")
                .ifBlank { "staff" }
                .take(50)
            val safeUsername = if (cleanUsername.length >= 2) cleanUsername else "u_$cleanUsername"

            db.collection("users").document(targetUserId).update(
                mapOf(
                    "role" to role.wireValue,
                    "active" to active,
                    "name" to cleanName,
                    "username" to safeUsername,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateUserCredentials(
        targetUserId: String,
        username: String,
        newPassword: String?
    ): Result<Unit> {
        val docPath = "users/$targetUserId"
        return try {
            requireUserId()
            require(targetUserId.isNotBlank()) { "User ID missing." }
            require(username.trim().length in 2..50) { "Username must be 2–50 characters." }
            val safeUsername = StaffAuthRepository.sanitizeUsername(username)

            val updates = mutableMapOf<String, Any>(
                "username" to safeUsername,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!newPassword.isNullOrBlank()) {
                require(newPassword.length >= 4) { "Password must be at least 4 characters." }
                val salt = StaffCredentialHasher.generateSalt()
                val hash = StaffCredentialHasher.hashPassword(newPassword, salt)
                updates["passcodeSalt"] = salt
                updates["passcodeHash"] = hash
            }

            db.collection("users").document(targetUserId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createStaffUser(
        name: String,
        username: String,
        password: String,
        role: UserRole,
        active: Boolean = true
    ): Result<Unit> {
        val safeUsername = StaffAuthRepository.sanitizeUsername(username)
        val docId = ("staff_" + safeUsername.replace(Regex("[^a-zA-Z0-9_-]"), "_")).take(128)
        val docPath = "users/$docId"
        return try {
            requireUserId()
            val cleanName = name.trim()
            require(cleanName.isNotEmpty()) { "Staff name is required." }
            require(username.trim().length in 2..50) { "Username must be 2–50 characters." }
            require(password.length >= 4) { "Password must be at least 4 characters." }

            val existing = db.collection("users")
                .whereEqualTo("username", safeUsername)
                .limit(1)
                .get()
                .await()
            if (!existing.isEmpty) {
                return Result.failure(IllegalArgumentException("Username @$safeUsername already exists."))
            }

            val salt = StaffCredentialHasher.generateSalt()
            val hash = StaffCredentialHasher.hashPassword(password, salt)
            db.collection("users").document(docId).set(
                mapOf(
                    "userId" to docId,
                    "username" to safeUsername,
                    "role" to role.wireValue,
                    "name" to cleanName.take(80),
                    "active" to active,
                    "passcodeSalt" to salt,
                    "passcodeHash" to hash,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteOrder(orderId: String): Result<Unit> {
        val docPath = "orders/$orderId"
        return try {
            requireUserId()
            require(orderId.isNotBlank()) { "Order ID missing." }
            db.collection("orders").document(orderId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    companion object {
        val BOOTSTRAPPED_OWNER_EMAILS = setOf(
            "njvigneshmoorthi94@gmail.com",
            "mechmoorthi007@gmail.com"
        )
    }
}
