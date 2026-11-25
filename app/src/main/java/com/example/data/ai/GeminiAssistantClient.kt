package com.example.data.ai

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.StockItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class GeminiCommandAction {
    SALE,
    SET_STOCK,
    ADD_ITEM,
    WEEKLY_REPORT,
    UNKNOWN
}

data class GeminiParsedCommand(
    val action: GeminiCommandAction,
    val stockId: String,
    val itemName: String,
    val quantity: Long,
    val minStock: Long,
    val unitPrice: Double,
    val explanation: String
)

object GeminiAssistantClient {

    private const val TAG = "GeminiAssistantClient"
    private const val MODEL_ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    fun isConfigured(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY.trim()
        return key.isNotEmpty() && key != "MY_GEMINI_API_KEY"
    }

    suspend fun parseInventoryCommand(
        commandText: String,
        stocks: List<StockItem>,
        defaultMinStock: Long
    ): Result<GeminiParsedCommand> {
        val trimmed = commandText.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Type a command like \"Sold 2 Sunflower Oil\"."))
        }
        val apiKey = BuildConfig.GEMINI_API_KEY.trim()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return Result.failure(
                IllegalStateException("Add your GEMINI_API_KEY in the AI Studio Secrets panel to enable the Gemini Assistant Bar.")
            )
        }

        return try {
            withTimeout(60_000L) {
                withContext(Dispatchers.IO) {
                    val inventoryCatalog = if (stocks.isEmpty()) {
                        "No items currently in inventory."
                    } else {
                        stocks.joinToString("\n") { s ->
                            val label = if (s.brand.equals("Thennaval", ignoreCase = true) || s.brand.isBlank()) {
                                s.partName
                            } else {
                                "${s.brand} ${s.partName}"
                            }
                            "- id=${s.id} | name=$label | stock=${s.quantity} | minStock=${s.lowStockLimit} | price=${s.unitPrice}"
                        }
                    }

                    val prompt = """
                        You are the inventory & billing parser for Thennaval Shop.
                        Convert the shopkeeper's command into a structured JSON action.

                        Current Inventory:
                        $inventoryCatalog

                        Default minStock: $defaultMinStock

                        Rules:
                        - Use action="sale" when selling/reducing stock (e.g. "Sold 2 Sunflower Oil" -> quantity=2, stockId matched from inventory).
                        - Use action="set_stock" when setting exact stock (e.g. "Set Sugar stock to 0" -> quantity=0, stockId matched from inventory).
                        - Use action="add_item" when adding a brand new item not in inventory.
                        - Use action="weekly_report" when asking to send weekly report.
                        - Use action="unknown" if unclear.
                        - Match the closest stock item id from Current Inventory whenever possible.

                        User Command: "$trimmed"
                    """.trimIndent()

                    val schema = JSONObject().apply {
                        put("type", "OBJECT")
                        put(
                            "properties",
                            JSONObject().apply {
                                put(
                                    "action",
                                    JSONObject().apply {
                                        put("type", "STRING")
                                        put("enum", JSONArray(listOf("sale", "set_stock", "add_item", "weekly_report", "unknown")))
                                    }
                                )
                                put("stockId", JSONObject().apply { put("type", "STRING") })
                                put("itemName", JSONObject().apply { put("type", "STRING") })
                                put("quantity", JSONObject().apply { put("type", "INTEGER") })
                                put("minStock", JSONObject().apply { put("type", "INTEGER") })
                                put("unitPrice", JSONObject().apply { put("type", "NUMBER") })
                                put("explanation", JSONObject().apply { put("type", "STRING") })
                            }
                        )
                        put("required", JSONArray(listOf("action", "stockId", "itemName", "quantity", "minStock", "unitPrice", "explanation")))
                    }

                    val bodyJson = JSONObject().apply {
                        put(
                            "contents",
                            JSONArray().put(
                                JSONObject().apply {
                                    put(
                                        "parts",
                                        JSONArray().put(JSONObject().apply { put("text", prompt) })
                                    )
                                }
                            )
                        )
                        put(
                            "generationConfig",
                            JSONObject().apply {
                                put("responseMimeType", "application/json")
                                put("responseSchema", schema)
                            }
                        )
                    }

                    val request = Request.Builder()
                        .url(MODEL_ENDPOINT)
                        .addHeader("x-goog-api-key", apiKey)
                        .post(bodyJson.toString().toRequestBody(JSON_MEDIA))
                        .build()

                    client.newCall(request).execute().use { response ->
                        val rawBody = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            Log.e(TAG, "Gemini API HTTP ${response.code}: $rawBody")
                            return@withContext Result.failure(
                                IllegalStateException("Gemini API error (HTTP ${response.code}). Check your GEMINI_API_KEY in Secrets.")
                            )
                        }
                        val root = JSONObject(rawBody)
                        val text = root.optJSONArray("candidates")
                            ?.optJSONObject(0)
                            ?.optJSONObject("content")
                            ?.optJSONArray("parts")
                            ?.optJSONObject(0)
                            ?.optString("text")
                            .orEmpty()
                        if (text.isBlank()) {
                            return@withContext Result.failure(IllegalStateException("Gemini returned an empty response."))
                        }
                        val parsed = JSONObject(text)
                        val action = when (parsed.optString("action")) {
                            "sale" -> GeminiCommandAction.SALE
                            "set_stock" -> GeminiCommandAction.SET_STOCK
                            "add_item" -> GeminiCommandAction.ADD_ITEM
                            "weekly_report" -> GeminiCommandAction.WEEKLY_REPORT
                            else -> GeminiCommandAction.UNKNOWN
                        }
                        Result.success(
                            GeminiParsedCommand(
                                action = action,
                                stockId = parsed.optString("stockId", ""),
                                itemName = parsed.optString("itemName", ""),
                                quantity = parsed.optLong("quantity", 0L),
                                minStock = parsed.optLong("minStock", defaultMinStock),
                                unitPrice = parsed.optDouble("unitPrice", 0.0),
                                explanation = parsed.optString("explanation", "")
                            )
                        )
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini command parsing failed", e)
            Result.failure(e)
        }
    }
}
