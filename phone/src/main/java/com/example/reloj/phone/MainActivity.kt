package com.example.reloj.phone

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.android.gms.wearable.Wearable
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val PATH_ADD_ACCOUNT = "/add_account"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No permitir capturas: la pantalla puede mostrar claves secretas
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            MaterialTheme {
                PhoneScreen()
            }
        }
    }
}

@Composable
fun PhoneScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf("Escanea el QR de un servicio o escribe la clave a mano.") }
    var sending by remember { mutableStateOf(false) }
    var issuer by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }

    fun send(uri: String, onOk: () -> Unit = {}) {
        scope.launch {
            sending = true
            val result = sendToWatch(context, uri)
            status = result.message
            if (result.ok) onOk()
            sending = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Autenticador del reloj", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Las cuentas se mandan directo al reloj. Esta app no guarda ninguna clave.",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                val options = GmsBarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build()
                GmsBarcodeScanning.getClient(context, options)
                    .startScan()
                    .addOnSuccessListener { barcode ->
                        val raw = barcode.rawValue.orEmpty()
                        if (raw.startsWith("otpauth://totp/")) {
                            send(raw)
                        } else {
                            status = "Ese QR no es de un autenticador TOTP. " +
                                    "Usa el QR que muestra cada servicio al activar el 2FA " +
                                    "(no el de exportar de Google Authenticator)."
                        }
                    }
                    .addOnCanceledListener { status = "Escaneo cancelado." }
                    .addOnFailureListener { e -> status = "No se pudo escanear: ${e.message}" }
            },
            enabled = !sending,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Escanear QR")
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text("O agrégala a mano", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = issuer,
            onValueChange = { issuer = it },
            label = { Text("Servicio (ej. GitHub)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = account,
            onValueChange = { account = it },
            label = { Text("Cuenta (ej. correo)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = secret,
            onValueChange = { secret = it },
            label = { Text("Clave secreta (Base32)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                val uri = buildUri(issuer, account, secret)
                if (uri == null) {
                    status = "Revisa los datos: pon un nombre y una clave válida " +
                            "(solo letras A-Z y números 2-7, mínimo 8 caracteres)."
                } else {
                    send(uri) {
                        issuer = ""
                        account = ""
                        secret = ""
                    }
                }
            },
            enabled = !sending,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Enviar al reloj")
        }

        Spacer(Modifier.height(20.dp))
        Text(status, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Arma la URI otpauth://totp/... a partir de los campos manuales. Devuelve null si algo no es válido. */
private fun buildUri(issuer: String, account: String, secret: String): String? {
    val clean = secret.filterNot { it == ' ' || it == '-' }.uppercase()
    val validSecret = clean.length >= 8 && clean.all { it in 'A'..'Z' || it in '2'..'7' }
    val name = account.trim().ifBlank { issuer.trim() }
    if (!validSecret || name.isBlank()) return null

    val label = if (issuer.isNotBlank() && account.isNotBlank()) {
        "${Uri.encode(issuer.trim())}:${Uri.encode(account.trim())}"
    } else {
        Uri.encode(name)
    }
    val issuerParam = if (issuer.isNotBlank()) "&issuer=${Uri.encode(issuer.trim())}" else ""
    return "otpauth://totp/$label?secret=$clean$issuerParam"
}

private data class SendResult(val ok: Boolean, val message: String)

private suspend fun sendToWatch(context: Context, uri: String): SendResult {
    return try {
        val nodes = Wearable.getNodeClient(context).connectedNodes.await()
        if (nodes.isEmpty()) {
            SendResult(false, "No hay un reloj conectado. Revisa que esté emparejado y encendido.")
        } else {
            val client = Wearable.getMessageClient(context)
            nodes.forEach { node ->
                client.sendMessage(node.id, PATH_ADD_ACCOUNT, uri.toByteArray(Charsets.UTF_8)).await()
            }
            SendResult(true, "Cuenta enviada al reloj ✓")
        }
    } catch (e: Exception) {
        SendResult(false, "Error al enviar: ${e.message ?: e.javaClass.simpleName}")
    }
}