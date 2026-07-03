package ua.hackteam.nickcatcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Оцінка вартості telegram-нікнейму.
 *
 * Основний шлях: Gemini 2.5 Flash з увімкненим інструментом googleSearch
 * (grounding) — модель сама шукає в інтернеті (аукціони Fragment, форуми
 * перепродажу, тощо) і повертає орієнтовну ціну в доларах.
 *
 * Якщо запит до Gemini не вдався (немає мережі, невалідний ключ, ліміт) —
 * автоматичний відкат (fallback) на локальну евристику, щоб кнопка в
 * застосунку ніколи просто не "мовчала".
 */
object NicknameValuator {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private const val JSON_MEDIA = "application/json; charset=utf-8"

    data class Result(val price: Int, val taken: Boolean, val source: String)

    suspend fun estimate(rawNickname: String): Result = withContext(Dispatchers.IO) {
        val nick = rawNickname.trim().removePrefix("@").lowercase()
        if (nick.isEmpty()) return@withContext Result(0, false, "empty")

        val taken = isTaken(nick)

        val geminiPrice = runCatching { askGemini(nick) }.getOrNull()
        if (geminiPrice != null && geminiPrice > 0) {
            return@withContext Result(geminiPrice, taken, "gemini")
        }

        // Fallback, якщо Gemini недоступний
        Result(computePriceHeuristic(nick, taken), taken, "heuristic")
    }

    private fun isTaken(nick: String): Boolean {
        if (nick.isEmpty()) return false
        return try {
            val request = Request.Builder()
                .url("https://t.me/$nick")
                .header("User-Agent", "Mozilla/5.0")
                .build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                !body.contains("If you have Telegram", ignoreCase = true) && resp.isSuccessful
            }
        } catch (e: IOException) {
            false
        }
    }

    /**
     * Звертається до Gemini 2.5 Flash з увімкненим Google Search grounding
     * і просить повернути СУВОРО одне число (ціну в доларах).
     */
    private fun askGemini(nick: String): Int? {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/" +
            "${Config.GEMINI_MODEL}:generateContent?key=${Config.GEMINI_API_KEY}"

        val prompt = """
            Знайди в інтернеті (аукціони Fragment, форуми перепродажу telegram-акаунтів,
            маркетплейси нікнеймів), скільки приблизно може коштувати telegram-юзернейм
            "@$nick" на вторинному ринку в доларах США.
            Врахуй довжину ніку, чи він "красивий" (короткий, без цифр/підкреслень,
            словникове слово), чи є попит на подібні ніки.
            Відповідь дай СТРОГО у форматі одного цілого числа доларів, без символу $,
            без пояснень, без тексту навколо. Наприклад: 45
            Якщо оцінити неможливо — відповідай 0.
        """.trimIndent()

        val requestJson = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text", prompt)
                ))
            ))
            // Дає моделі реально шукати в Google, а не вигадувати з пам'яті
            put("tools", JSONArray().put(
                JSONObject().put("google_search", JSONObject())
            ))
            put("generationConfig", JSONObject().put("temperature", 0.3))
        }

        val body = requestJson.toString().toRequestBody(JSON_MEDIA.toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .header("Content-Type", "application/json")
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val respBody = resp.body?.string() ?: return null
            val json = JSONObject(respBody)
            val text = json
                .optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")?.optJSONObject(0)
                ?.optString("text") ?: return null

            val match = Regex("\\d+").find(text) ?: return null
            return match.value.toIntOrNull()
        }
    }

    // ---- Локальна евристика (запасний варіант) ----

    private fun computePriceHeuristic(nick: String, taken: Boolean): Int {
        if (nick.isEmpty()) return 0
        var base = when (nick.length) {
            in 1..3 -> 5000
            4 -> 2000
            5 -> 800
            6 -> 300
            7 -> 120
            8 -> 60
            in 9..10 -> 30
            else -> 10
        }
        if (nick.any { it.isDigit() }) base = (base * 0.6).toInt()
        if (nick.contains("_")) base = (base * 0.8).toInt()
        if (taken) base = (base * 1.4).toInt()
        if (Regex("(.)\\1{1,}").containsMatchIn(nick)) base = (base * 1.2).toInt()
        val hotWords = listOf("crypto", "btc", "wallet", "nft", "p2p", "trade", "bank", "vip", "gold", "ton")
        if (hotWords.any { nick.contains(it) }) base = (base * 1.5).toInt()
        return max(1, base)
    }
}
