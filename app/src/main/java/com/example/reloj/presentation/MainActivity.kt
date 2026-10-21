package com.example.reloj.presentation

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private lateinit var vault: Vault
    private val accounts = mutableStateListOf<Account>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Evita capturas de pantalla de los códigos
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        vault = Vault(this)
        accounts.addAll(vault.load())
        handleAddIntent(intent)

        setContent {
            // Recarga la lista cuando el reloj recibe una cuenta nueva desde el teléfono
            LaunchedEffect(Unit) {
                AccountBus.version.collect {
                    accounts.clear()
                    accounts.addAll(vault.load())
                }
            }
            AuthApp(
                accounts = accounts,
                onDelete = { acc ->
                    accounts.remove(acc)
                    vault.save(accounts.toList())
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAddIntent(intent)
    }

    /**
     * SOLO PARA DESARROLLO: permite agregar una cuenta por adb con
     *   adb shell am start -n com.example.reloj/.presentation.MainActivity --es add "otpauth://..."
     * Se ignora en builds de release para que otras apps no puedan inyectar cuentas.
     */
    private fun handleAddIntent(intent: Intent?) {
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val uri = intent?.getStringExtra("add") ?: return
        if (!debuggable) return
        try {
            accounts.add(Account.fromUri(uri))
            vault.save(accounts.toList())
            Toast.makeText(this, "Cuenta agregada", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "Error", Toast.LENGTH_LONG).show()
        }
        intent.removeExtra("add")
    }
}

@Composable
fun AuthApp(accounts: List<Account>, onDelete: (Account) -> Unit) {
    MaterialTheme {
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        var confirmDelete by remember { mutableStateOf<Account?>(null) }

        LaunchedEffect(Unit) {
            while (true) {
                now = System.currentTimeMillis()
                delay(250)
            }
        }

        // Convierte cada cuenta en los datos que pinta el diseño
        val items = accounts.map { acc ->
            val code = try {
                Totp.generate(
                    Base32.decode(acc.secret), now, acc.digits,
                    acc.period.toLong(), acc.algorithm
                )
            } catch (e: Exception) {
                "ERROR"
            }
            CodeItem(
                name = acc.issuer.ifBlank { acc.name },
                code = code,
                secondsLeft = Totp.secondsRemaining(acc.period.toLong(), now).toInt(),
                period = acc.period.toInt()
            )
        }

        Box(Modifier.fillMaxSize().background(Color.White)) {
            AuthenticatorScreen(
                items = items,
                onLongPress = { index -> confirmDelete = accounts.getOrNull(index) }
            )

            confirmDelete?.let { acc ->
                Column(
                    Modifier.fillMaxSize().background(Color.White).padding(20.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "¿Eliminar ${acc.issuer.ifBlank { acc.name }}?",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { confirmDelete = null },
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = Color(0xFFE3E3E3),
                                contentColor = Color.Black
                            )
                        ) { Text("No") }
                        Button(
                            onClick = { onDelete(acc); confirmDelete = null },
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = Color.Black,
                                contentColor = Color.White
                            )
                        ) { Text("Sí") }
                    }
                }
            }
        }
    }
}