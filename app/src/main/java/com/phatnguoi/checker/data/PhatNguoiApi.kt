package com.phatnguoi.checker.data

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.phatnguoi.checker.model.ViolationDetail
import com.phatnguoi.checker.model.ViolationResult
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class PhatNguoiApi {

    private val gson = Gson()

    private val cookieStore = mutableMapOf<String, List<Cookie>>()
    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookieStore[url.host] = cookies
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore[url.host] ?: emptyList()
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .cookieJar(cookieJar)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("Accept-Language", "vi-VN,vi;q=0.9,en;q=0.8")
                .header("Origin", "https://phatnguoi.app")
                .header("Referer", "https://phatnguoi.app/")
                .header("X-Requested-With", "XMLHttpRequest")
                .build()
            chain.proceed(req)
        }
        .build()

    private fun mapVehicleType(type: String): String {
        val normalized = Normalizer.normalize(type.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")

        return if (
            normalized.contains("car") ||
            normalized.contains("oto") ||
            normalized.contains("o to")
        ) {
            "car"
        } else {
            "moto"
        }
    }

    fun checkViolation(licensePlate: String, vehicleType: String = "Xe o to"): ViolationResult {
        val plate = licensePlate.trim()
        val vType = mapVehicleType(vehicleType)

        Log.d("PhatNguoiApi", "POST ajax to fetch nonce...")
        val nonce = fetchFreshNonce()
        if (nonce.isEmpty()) {
            Log.e("PhatNguoiApi", "Failed to fetch nonce")
            throw IllegalStateException("Cannot fetch phatnguoi.app nonce")
        }
        Log.d("PhatNguoiApi", "Nonce: $nonce, vehicleType: $vType")

        val formBody = FormBody.Builder()
            .add("action", "phatnguoi_search")
            .add("nonce", nonce)
            .add("license_plate", plate)
            .add("vehicle_type", vType)
            .build()

        val response = client.newCall(
            Request.Builder()
                .url("https://phatnguoi.app/wp-admin/admin-ajax.php")
                .post(formBody)
                .build()
        ).execute()

        val body = response.body?.string() ?: ""
        Log.d("PhatNguoiApi", "Response: $body")

        if (!response.isSuccessful) {
            throw IllegalStateException("phatnguoi.app search failed: HTTP ${response.code}")
        }

        return parseJson(plate, body)
    }

    private fun fetchFreshNonce(): String {
        val formBody = FormBody.Builder()
            .add("action", "phatnguoi_get_nonce")
            .build()

        val response = client.newCall(
            Request.Builder()
                .url("https://phatnguoi.app/wp-admin/admin-ajax.php")
                .post(formBody)
                .build()
        ).execute()

        val body = response.body?.string() ?: ""
        Log.d("PhatNguoiApi", "Nonce response: $body")

        if (response.isSuccessful) {
            try {
                val root = gson.fromJson(body, JsonObject::class.java)
                val data = root.getAsJsonObject("data")
                val nonce = data?.get("nonce")?.asString ?: ""
                if (nonce.isNotEmpty()) return nonce
            } catch (e: Exception) {
                Log.e("PhatNguoiApi", "Nonce parse error: ${e.message}")
            }
        }

        return fetchNonceFromHomepage()
    }

    private fun fetchNonceFromHomepage(): String {
        val homeHtml = client.newCall(
            Request.Builder()
                .url("https://phatnguoi.app/")
                .get()
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()
        ).execute().body?.string() ?: ""

        return extractNonce(homeHtml)
    }

    private fun extractNonce(html: String): String {
        val p1 = Pattern.compile("""nonce['":\s]+['"]?([a-f0-9]+)['"]""", Pattern.CASE_INSENSITIVE)
        val m1 = p1.matcher(html)
        if (m1.find()) return m1.group(1) ?: ""

        val p2 = Pattern.compile(
            """name=["']nonce["'][^>]*value=["']([a-f0-9]+)["']|value=["']([a-f0-9]+)["'][^>]*name=["']nonce["']""",
            Pattern.CASE_INSENSITIVE
        )
        val m2 = p2.matcher(html)
        if (m2.find()) return m2.group(1) ?: m2.group(2) ?: ""

        return ""
    }

    private fun parseJson(licensePlate: String, body: String): ViolationResult {
        if (body.isEmpty()) {
            throw IllegalStateException("phatnguoi.app returned an empty response")
        }

        return try {
            val root = gson.fromJson(body, JsonObject::class.java)
            if (!root.get("success").asBoolean) {
                val message = root.get("data")?.asJsonObject?.get("message")?.asString
                    ?: "phatnguoi.app returned success=false"
                throw IllegalStateException(message)
            }

            val data = root.getAsJsonObject("data")
            val total = data.get("total_violations").asInt
            val unpaid = data.get("unpaid_count").asInt
            val paid = data.get("paid_count").asInt
            val vArray = data.getAsJsonArray("violations")

            val violations = vArray.map { el ->
                val v = el.asJsonObject
                val resolutionArr = v.getAsJsonArray("resolution_location")
                val resolution = if (resolutionArr != null && resolutionArr.size() > 0) {
                    resolutionArr[0].asString
                } else {
                    ""
                }

                ViolationDetail(
                    licensePlate = v.get("plate")?.asString ?: licensePlate,
                    status = v.get("status_text")?.asString ?: "",
                    vehicleType = v.get("vehicle_type")?.asString ?: "",
                    plateColor = v.get("plate_color")?.asString ?: "",
                    behavior = v.get("title")?.asString ?: "",
                    violationTime = v.get("time")?.asString ?: "",
                    violationPlace = v.get("location")?.asString ?: "",
                    detectingUnit = v.get("unit")?.asString ?: "",
                    resolvingUnit = resolution,
                    resolvingAddress = "",
                    resolvingPhone = v.get("phone")?.asString ?: ""
                )
            }

            Log.d("PhatNguoiApi", "$licensePlate -> total=$total paid=$paid unpaid=$unpaid")
            ViolationResult(
                licensePlate = licensePlate,
                totalViolations = total,
                processedViolations = paid,
                unprocessedViolations = unpaid,
                violations = violations
            )
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            Log.e("PhatNguoiApi", "Parse error: ${e.message}")
            throw IllegalStateException("Cannot parse phatnguoi.app response", e)
        }
    }
}
