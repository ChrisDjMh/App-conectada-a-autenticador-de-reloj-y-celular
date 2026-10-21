package com.example.reloj.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text

/** Datos que necesita la pantalla para pintar una cuenta. */
data class CodeItem(
    val name: String,       // "GitHub", "Google", ...
    val code: String,       // "123456"
    val secondsLeft: Int,   // 30..0
    val period: Int = 30
)

private val Ink = Color.Black
private val Paper = Color.White
private val Track = Color(0xFFE3E3E3)
private val Alert = Color(0xFFD32F2F)

/**
 * Pantalla completa: una cuenta por página, se cambia deslizando hacia arriba/abajo.
 * [onLongPress] recibe el índice de la cuenta mantenida presionada.
 */
@Composable
fun AuthenticatorScreen(items: List<CodeItem>, onLongPress: (Int) -> Unit = {}) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
    ) {
        if (items.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AppHeader()
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Sin cuentas.\nAgrega una desde el teléfono\n(o por adb en desarrollo).",
                    color = Ink,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            val pagerState = rememberPagerState(pageCount = { items.size })
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                CodePage(items[page], onLongPress = { onLongPress(page) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CodePage(item: CodeItem, onLongPress: () -> Unit = {}) {
    val urgent = item.secondsLeft <= 5
    val accent = if (urgent) Alert else Ink
    val progress = (item.secondsLeft.toFloat() / item.period).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
            .padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppHeader()
        Spacer(Modifier.height(8.dp))

        // Tarjeta negra con nombre + código
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Ink)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = item.name,
                color = Paper,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatCode(item.code),
                color = Paper,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                softWrap = false
            )
        }

        Spacer(Modifier.height(10.dp))

        // Barra de tiempo
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(50))
                .background(Track)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .clip(RoundedCornerShape(50))
                    .background(accent)
            )
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = "%02d:%02d".format(item.secondsLeft / 60, item.secondsLeft % 60),
            color = accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** "Autenticador" + icono de reloj. */
@Composable
private fun AppHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Autenticador",
            color = Ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Spacer(Modifier.width(6.dp))
        ClockIcon()
    }
}

/** Círculo negro con manecillas blancas, dibujado a mano (sin dependencias de íconos). */
@Composable
private fun ClockIcon() {
    Canvas(modifier = Modifier.size(20.dp)) {
        val r = size.minDimension / 2f
        val c = center
        val stroke = r * 0.16f
        drawCircle(color = Ink)
        drawLine(
            color = Paper,
            start = c,
            end = Offset(c.x, c.y - r * 0.55f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Paper,
            start = c,
            end = Offset(c.x + r * 0.42f, c.y + r * 0.32f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

private fun formatCode(code: String): String =
    if (code.length == 6) code.substring(0, 3) + " " + code.substring(3) else code

@Preview(device = Devices.WEAR_OS_SMALL_ROUND, showSystemUi = true)
@Composable
private fun PreviewAuthenticator() {
    AuthenticatorScreen(
        listOf(
            CodeItem(name = "Nombre", code = "000000", secondsLeft = 30),
            CodeItem(name = "GitHub", code = "482913", secondsLeft = 4)
        )
    )
}