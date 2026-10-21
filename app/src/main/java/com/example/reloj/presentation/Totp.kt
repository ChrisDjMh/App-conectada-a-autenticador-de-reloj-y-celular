package com.example.reloj.presentation

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun decode(input: String): ByteArray {
        val clean = input.uppercase().filter { it != '=' && !it.isWhitespace() && it != '-' }
        var buffer = 0
        var bits = 0
        val out = java.io.ByteArrayOutputStream()
        for (c in clean) {
            val v = ALPHABET.indexOf(c)
            require(v >= 0) { "Carácter inválido en el secreto: $c" }
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xFF)
                bits -= 8
            }
        }
        return out.toByteArray()
    }
}

/** Implementación de RFC 6238 (TOTP) sobre RFC 4226 (HOTP). */
object Totp {
    fun generate(
        secret: ByteArray,
        timeMillis: Long = System.currentTimeMillis(),
        digits: Int = 6,
        periodSec: Long = 30,
        algorithm: String = "HmacSHA1"
    ): String {
        val counter = timeMillis / 1000 / periodSec
        val msg = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance(algorithm)
        mac.init(SecretKeySpec(secret, algorithm))
        val h = mac.doFinal(msg)
        val o = h.last().toInt() and 0x0F
        val bin = ((h[o].toInt() and 0x7F) shl 24) or
                ((h[o + 1].toInt() and 0xFF) shl 16) or
                ((h[o + 2].toInt() and 0xFF) shl 8) or
                (h[o + 3].toInt() and 0xFF)
        val mod = 10.0.pow(digits).toInt()
        return (bin % mod).toString().padStart(digits, '0')
    }

    fun secondsRemaining(periodSec: Long = 30, timeMillis: Long = System.currentTimeMillis()): Int =
        (periodSec - (timeMillis / 1000) % periodSec).toInt()
}