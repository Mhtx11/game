package com.spcollector.gacha.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

val Crimson = Color(0xFFDC143C)
val Orange = Color(0xFFFF8C00)
val Bg = Color(0xFF0D0D12)
val Surf = Color(0xFF1A1A24)

@Composable
fun AppTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(primary = Crimson, secondary = Orange, background = Bg, surface = Surf,
        onBackground = Color.White, onSurface = Color.White, onPrimary = Color.White),
    content = content,
)

fun pct(x: Double) = "%.2f%%".format(x * 100)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Page(title: String, nav: NavController, scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { TextButton(onClick = { nav.popBackStack() }) { Text("←", color = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surf, titleContentColor = Color.White),
            )
        },
        containerColor = Bg,
    ) { pad ->
        var m = Modifier.padding(pad).padding(16.dp).fillMaxSize()
        if (scroll) m = m.verticalScroll(rememberScrollState())
        Column(m, verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun Panel(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Surf), border = BorderStroke(1.dp, Orange.copy(alpha = 0.35f))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (title != null) Text(title, color = Orange, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
fun Field(label: String, value: String, onChange: (String) -> Unit, number: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth()) =
    OutlinedTextField(value, onChange, modifier = modifier, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text))

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) =
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1f)); Switch(checked, onChange)
    }

@Composable
fun BigButton(text: String, enabled: Boolean = true, onClick: () -> Unit) =
    Button(onClick, Modifier.fillMaxWidth(), enabled = enabled, colors = ButtonDefaults.buttonColors(containerColor = Crimson)) { Text(text) }

@Composable
fun ConfigNeeded(problems: List<String>, nav: NavController) = Panel("CONFIGURATION REQUIRED") {
    Text("Parameter gacha belum lengkap. Aplikasi tidak memakai angka tebakan.")
    problems.forEach { Text("• $it", color = Orange) }
    BigButton("Buka Settings") { nav.navigate("settings") }
}

@Composable
fun CdfChart(series: List<Pair<DoubleArray, Color>>, maxN: Int) {
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        series.forEach { (cdf, color) ->
            val n = min(maxN, cdf.size - 1).coerceAtLeast(1)
            val path = Path()
            for (i in 0..n) {
                val x = size.width * i / n
                val y = size.height * (1f - cdf[i].toFloat())
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = 4f))
        }
        drawLine(Color.Gray.copy(alpha = 0.4f), Offset(0f, size.height), Offset(size.width, size.height), 2f)
    }
}

@Composable
fun Preview(uri: String) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val o = BitmapFactory.Options().apply { inSampleSize = 4 }
                ctx.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, o)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    bmp?.let { Image(it, "Screenshot", Modifier.fillMaxWidth().heightIn(max = 160.dp), contentScale = ContentScale.Fit) }
}
