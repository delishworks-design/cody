package com.termux.app.install

import android.util.Base64
import android.util.Log
import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Mirrors the Android trust store into the Termux CA bundle.
 *
 * Why this exists: on networks that intercept TLS (corporate SSL inspection), the trust anchor is
 * an organization root CA installed on the device as a *user* certificate. Android apps trust it,
 * but the tools inside the Termux prefix (curl, apt, python) verify against their own bundle at
 * [TermuxPaths.CA_BUNDLE] and know nothing about the Android trust store — so every HTTPS download
 * fails with "certificate verify failed" while the browser works fine.
 *
 * We fix that by asking Android which CAs it currently trusts (system **and** user, thanks to
 * `res/xml/network_security_config.xml` declaring `<certificates src="user"/>`) and writing them
 * out as PEM. [EnvironmentProvisioner] then merges this file into the Termux bundle as the setup
 * step "Syncing TLS certificates", and copies it into the Debian rootfs so the container can
 * verify TLS too.
 */
object TlsTrustSync {
    private const val TAG = "TlsTrustSync"

    /**
     * Every certificate the default trust manager accepts: the system store plus any user-installed
     * (for example corporate/MDM) roots. Empty on failure rather than throwing, so setup can still
     * proceed with whatever public CAs the Termux bootstrap already shipped.
     */
    fun trustAnchors(): List<X509Certificate> = try {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        // null KeyStore => the default (network-security-config-backed) trust store for this app.
        factory.init(null as KeyStore?)
        factory.trustManagers
            .filterIsInstance<X509TrustManager>()
            .flatMap { it.acceptedIssuers.toList() }
    } catch (t: Throwable) {
        Log.e(TAG, "Unable to enumerate Android trust anchors", t)
        emptyList()
    }

    /** DER -> PEM (one block per certificate). */
    fun toPem(certs: List<X509Certificate>): String = buildString {
        for (cert in certs) {
            // Base64.DEFAULT wraps long lines, which every PEM consumer accepts.
            append("-----BEGIN CERTIFICATE-----\n")
            append(Base64.encodeToString(cert.encoded, Base64.DEFAULT))
            append("-----END CERTIFICATE-----\n")
        }
    }

    /**
     * Writes the trust anchors to [TermuxPaths.CA_ANCHORS]. Idempotent: the file is rewritten from
     * scratch each run so repeated setup attempts cannot accumulate duplicates.
     *
     * @return the number of anchors written.
     */
    fun installIntoTermux(): Int {
        return try {
            val anchors = trustAnchors()
            val dir = File(TermuxPaths.TLS_DIR)
            if (!dir.isDirectory && !dir.mkdirs()) {
                Log.w(TAG, "Could not create ${dir.path}; trust anchors not written")
                0
            } else {
                File(TermuxPaths.CA_ANCHORS).writeText(toPem(anchors))
                Log.i(TAG, "Wrote ${anchors.size} trust anchors to ${TermuxPaths.CA_ANCHORS}")
                anchors.size
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to install trust anchors", t)
            0
        }
    }
}
