package com.locationsetter.app.data.subscription

import com.locationsetter.app.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class LicenseResult(
    val valid: Boolean,
    val instanceId: String?,
    val errorMessage: String?
)

/**
 * Thin client for Lemon Squeezy's public license-key API — no secret key required, so it's safe
 * to call directly from the app with nothing to steal out of the APK. Response field names follow
 * Lemon Squeezy's documented license API shape; verify against the live dashboard once a real
 * product/license exists, since this was built without a live account to test against.
 */
object LemonSqueezyApi {

    suspend fun activate(licenseKey: String, instanceName: String): LicenseResult =
        post(Constants.LEMONSQUEEZY_ACTIVATE_URL, mapOf("license_key" to licenseKey, "instance_name" to instanceName))

    suspend fun validate(licenseKey: String, instanceId: String?): LicenseResult {
        val params = mutableMapOf("license_key" to licenseKey)
        if (instanceId != null) params["instance_id"] = instanceId
        return post(Constants.LEMONSQUEEZY_VALIDATE_URL, params)
    }

    private suspend fun post(urlString: String, params: Map<String, String>): LicenseResult =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val body = params.entries.joinToString("&") { (key, value) ->
                    "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
                }
                connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                }
                OutputStreamWriter(connection.outputStream).use { it.write(body) }

                val responseCode = connection.responseCode
                val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                parseResponse(responseText)
            } catch (e: Exception) {
                LicenseResult(valid = false, instanceId = null, errorMessage = e.message ?: "Network error")
            } finally {
                connection?.disconnect()
            }
        }

    private fun parseResponse(responseText: String): LicenseResult {
        if (responseText.isBlank()) {
            return LicenseResult(valid = false, instanceId = null, errorMessage = "Empty response from server")
        }
        return try {
            val json = JSONObject(responseText)
            val valid = json.optBoolean("valid", false)
            val instanceId = json.optJSONObject("instance")?.optString("id")
            val error = json.optString("error", null)
            LicenseResult(valid = valid, instanceId = instanceId, errorMessage = error)
        } catch (e: Exception) {
            LicenseResult(valid = false, instanceId = null, errorMessage = "Unexpected response format")
        }
    }
}
