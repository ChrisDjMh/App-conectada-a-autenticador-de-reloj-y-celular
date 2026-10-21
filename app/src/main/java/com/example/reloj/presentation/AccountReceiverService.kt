package com.example.reloj.presentation

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Avisa a la pantalla que la bóveda cambió y debe recargar la lista. */
object AccountBus {
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    fun notifyChanged() {
        _version.update { it + 1 }
    }
}

/**
 * Recibe las cuentas que manda la app del teléfono (mensaje "/add_account"
 * con una URI otpauth://totp/...) y las guarda cifradas en la bóveda.
 */
class AccountReceiverService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != PATH_ADD_ACCOUNT) return

        val message = try {
            val acc = Account.fromUri(String(event.data, Charsets.UTF_8))
            val vault = Vault(applicationContext)
            val current = vault.load()
            val exists = current.any {
                it.secret == acc.secret && it.issuer == acc.issuer && it.name == acc.name
            }
            if (exists) {
                "Esa cuenta ya estaba"
            } else {
                vault.save(current + acc)
                AccountBus.notifyChanged()
                "Cuenta agregada"
            }
        } catch (e: Exception) {
            "No se pudo agregar"
        }

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val PATH_ADD_ACCOUNT = "/add_account"
    }
}