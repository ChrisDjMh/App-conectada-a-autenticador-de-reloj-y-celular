package com.example.reloj.presentation

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Account(
    val issuer: String,
    val name: String,
    val secret: String,          // Base32
    val digits: Int = 6,
    val period: Int = 30,
    val algorithm: String = "HmacSHA1"
) {
    companion object {
        /** Parsea otpauth://totp/Issuer:cuenta?secret=XXXX&issuer=Issuer&digits=6&period=30 */
        fun fromUri(raw: String): Account {
            val uri = Uri.parse(raw)
            require(uri.scheme == "otpauth" && uri.host == "totp") { "URI no válida (solo TOTP)" }
            val label = (uri.path ?: "").trimStart('/')
            val parts = label.split(":", limit = 2)
            val issuerFromLabel = if (parts.size == 2) parts[0] else ""
            val name = if (parts.size == 2) parts[1].trim() else parts[0]
            val secret = requireNotNull(uri.getQueryParameter("secret")) { "Falta el secreto" }
            Base32.decode(secret) // valida el formato
            val algo = when (uri.getQueryParameter("algorithm")?.uppercase()) {
                "SHA256" -> "HmacSHA256"
                "SHA512" -> "HmacSHA512"
                else -> "HmacSHA1"
            }
            return Account(
                issuer = uri.getQueryParameter("issuer") ?: issuerFromLabel,
                name = name,
                secret = secret,
                digits = uri.getQueryParameter("digits")?.toIntOrNull() ?: 6,
                period = uri.getQueryParameter("period")?.toIntOrNull() ?: 30,
                algorithm = algo
            )
        }
    }
}

/** Guarda las cuentas cifradas con AES-256-GCM; la llave vive en el Keystore del reloj. */
class Vault(context: Context) {
    private val prefs = context.getSharedPreferences("vault", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(
            KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return kg.generateKey()
    }

    fun load(): List<Account> {
        val stored = prefs.getString(PREF, null) ?: return emptyList()
        return try {
            val data = Base64.decode(stored, Base64.NO_WRAP)
            val iv = data.copyOfRange(0, 12)
            val ct = data.copyOfRange(12, data.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            val json = JSONArray(String(cipher.doFinal(ct), Charsets.UTF_8))
            List(json.length()) { i ->
                val o = json.getJSONObject(i)
                Account(
                    o.getString("issuer"), o.getString("name"), o.getString("secret"),
                    o.getInt("digits"), o.getInt("period"), o.getString("algorithm")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(accounts: List<Account>) {
        val json = JSONArray()
        accounts.forEach {
            json.put(
                JSONObject()
                    .put("issuer", it.issuer).put("name", it.name).put("secret", it.secret)
                    .put("digits", it.digits).put("period", it.period).put("algorithm", it.algorithm)
            )
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ct = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        prefs.edit().putString(PREF, Base64.encodeToString(cipher.iv + ct, Base64.NO_WRAP)).apply()
    }

    private companion object {
        const val ALIAS = "totp_vault_key"
        const val PREF = "accounts"
    }
}