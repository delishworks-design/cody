package com.termux.app.features

import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * GitHub device-code sign-in, the same flow `gh auth login --web` uses.
 *
 * All methods are blocking HTTP; the caller runs them on `Dispatchers.IO`. The returned token
 * should be stored by the caller in Cody's `CredentialStore` under the key `GH_TOKEN`.
 */
object GitHubDeviceLogin {
    data class Flow(
        val userCode: String,
        val verificationUri: String,
        val deviceCode: String,
        val intervalSeconds: Int,
        val expiresInSeconds: Int
    )

    sealed class Outcome {
        data class Success(val token: String) : Outcome()
        object Pending : Outcome()
        object SlowDown : Outcome()
        object Denied : Outcome()
        object Expired : Outcome()
        data class Failed(val message: String) : Outcome()
    }

    const val AUTH_URL: String = "https://github.com/login/device"

    private const val DEVICE_CODE_URL = "https://github.com/login/device/code"
    private const val TOKEN_URL = "https://github.com/login/oauth/access_token"
    private const val CLIENT_ID = "178c6fc778ccc68e1d6a"
    private const val SCOPES = "repo read:org gist workflow"
    private const val TIMEOUT_MS = 15_000

    /** Step one: ask GitHub for a device code and the user-facing code to enter at [AUTH_URL]. */
    fun start(): Flow {
        val json = post(
            DEVICE_CODE_URL,
            mapOf("client_id" to CLIENT_ID, "scope" to SCOPES)
        )
        val deviceCode = json.optString("device_code", "")
        if (deviceCode.isBlank()) throw IOException(json.errorMessage("GitHub did not give a device code"))
        val userCode = json.optString("user_code", "")
        if (userCode.isBlank()) throw IOException(json.errorMessage("GitHub did not give a user code"))
        return Flow(
            userCode = userCode,
            verificationUri = json.optString("verification_uri", "").ifBlank { AUTH_URL },
            deviceCode = deviceCode,
            intervalSeconds = json.optInt("interval", 5).coerceAtLeast(1),
            expiresInSeconds = json.optInt("expires_in", 900)
        )
    }

    /** Step two: poll once with the device code. The caller waits [Flow.intervalSeconds] between calls. */
    fun poll(flow: Flow): Outcome {
        val json = post(
            TOKEN_URL,
            mapOf(
                "client_id" to CLIENT_ID,
                "device_code" to flow.deviceCode,
                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"
            )
        )
        val token = json.optString("access_token", "")
        if (token.isNotBlank()) return Outcome.Success(token)
        return when (json.optString("error", "")) {
            "authorization_pending" -> Outcome.Pending
            "slow_down" -> Outcome.SlowDown
            "access_denied" -> Outcome.Denied
            "expired_token" -> Outcome.Expired
            else -> Outcome.Failed(json.errorMessage("GitHub sign-in failed"))
        }
    }

    private fun post(url: String, form: Map<String, String>): JSONObject {
        val body = form.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }.toByteArray(Charsets.UTF_8)
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.doOutput = true
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        try {
            connection.outputStream.use { it.write(body) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            return try {
                JSONObject(text)
            } catch (e: JSONException) {
                throw IOException(text.ifBlank { "GitHub returned an empty response" })
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun JSONObject.errorMessage(fallback: String): String {
        val description = optString("error_description", "")
        if (description.isNotBlank()) return description
        val error = optString("error", "")
        if (error.isNotBlank()) return error
        return fallback
    }
}
