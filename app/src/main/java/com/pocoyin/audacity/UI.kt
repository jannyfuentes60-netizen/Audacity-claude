package com.pocoyin.audacity

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.floor
import kotlin.math.max

val Bg = Color(0xFF1E2027)
val Panel = Color(0xFF2A2D36)
val Panel2 = Color(0xFF323640)
val Panel3 = Color(0xFF3F4452)
val TrackBg = Color(0xFF181A20)
val Accent = Color(0xFF5B7CFA)
val Fg = Color(0xFFE6E8EE)
val Dim = Color(0xFF9AA0AE)
val TrackColors = listOf(Color(0xFF5B7CFA), Color(0xFF3FB6A8), Color(0xFFE08A4B), Color(0xFFB36BF0), Color(0xFFE35D8A))
val HeaderW = 112.dp

fun fmtClock(sec: Double): String {
    val h = (sec / 3600).toInt(); val m = ((sec % 3600) / 60).toInt(); val s = sec % 60
    return "%02d:%02d:%06.3f".format(h, m, s)
}

@Composable
fun MenuBtn(label: String, items: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        Text(label, color = Fg, fontSize = 13.sp,
            modifier = Modifier.clickable { open = true }.padding(horizontal = 12.dp, vertical = 10.dp))
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { (t, a) -> DropdownMenuItem(text = { Text(t) }, onClick = { open = false; a() }) }
        }
    }
}

@Composable
fun TBtn(kind: String, active: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(RoundedCornerShape(6.dp))
            .background(if (active) Accent else Panel3).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(16.dp)) {
            val c = if (kind == "rec") Color(0xFFFF5A5F) else Color.White
            val w = size.width; val h = size.height
            when (kind) {
                "play" -> drawPath(Path().apply { moveTo(0f, 0f); lineTo(w, h / 2); lineTo(0f, h); close() }, c)
                "pause" -> {
                    drawRect(c, Offset(0f, 0f), Size(w * 0.35f, h))
                    drawRect(c, Offset(w * 0.65f, 0f), Size(w * 0.35f, h))
                }
                "stop" -> drawRect(c)
                "rec" -> drawCircle(c)
                "start" -> {
                    drawRect(c, Offset(0f, 0f), Size(w * 0.18f, h))
                    drawPath(Path().apply { moveTo(w, 0f); lineTo(w * 0.25f, h / 2); lineTo(w, h); close() }, c)
                }
                "end" -> {
                    drawRect(c, Offset(w * 0.82f, 0f), Size(w * 0.18f, h))
                    drawPath(Path().apply { moveTo(0f, 0f); lineTo(w * 0.75f, h / 2); lineTo(0f, h); close() }, c)
                }
            }
        }
    }
}

@Composable
fun SmallBtn(label: String, onClick: () -> Unit) {
    Box(
        Modifier.height(34.dp).clip(RoundedCornerShape(6.dp)).background(Panel3)
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, color = Fg, fontSize = 13.sp) }
}

@Composable
fun Chip(t: String, on: Boolean, f: () -> Unit) {
    Box(
        Modifier.size(26.dp).clip(RoundedCornerShape(4.dp)).background(if (on) Accent else Panel3).clickable(onClick = f),
        contentAlignment = Alignment.Center
    ) { Text(t, color = Fg, fontSize = 12.sp) }
}

@Composable
fun TrackRow(st: AppState, i: Int, t: Track, onSize: (Float) -> Unit) {
    val rv = st.rev
    val col = TrackColors[t.color % TrackColors.size]
    val active = st.activeTrack == i
    Row(Modifier.fillMaxWidth().height(130.dp).padding(bottom = 2.dp)) {
        Column(
            Modifier.width(HeaderW).fillMaxHeight().background(if (active) Panel3 else Panel)
                .clickable { st.activeTrack = i }.padding(6.dp)
        ) {
            Text(t.name, color = Fg, fontSize = 12.sp, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Chip("M", t.mute) { t.mute = !t.mute; st.rev++ }
                Chip("S", t.solo) { t.solo = !t.solo; st.rev++ }
            }
            Slider(value = t.gain, onValueChange = { t.gain = it; st.rev++ }, valueRange = 0f..2f)
            Text("Vol ${(t.gain * 100).toInt()}%", color = Dim, fontSize = 10.sp)
        }
        Canvas(
            Modifier.weight(1f).fillMaxHeight().background(TrackBg)
                .onSizeChanged { onSize(it.width.toFloat()) }
                .pointerInput(i) {
                    detectTapGestures { o -> st.activeTrack = i; st.selA = -1; st.head = st.xToSample(o.x) }
                }
                .pointerInput(i) {
                    detectDragGestures(
                        onDragStart = { o -> st.activeTrack = i; val s = st.xToSample(o.x); st.selA = s; st.selB = s },
                        onDrag = { ch, _ -> ch.consume(); st.selB = st.xToSample(ch.position.x) }
                    )
                }
        ) {
            val rv2 = st.rev
            val w = size.width; val h = size.height; val mid = h / 2f
            val d = t.data; val spp = st.spp; val sc = st.scrollPx
            val xs = (0f - sc).coerceAtLeast(0f)
            val xe = (d.size / spp - sc).coerceAtMost(w)
            drawLine(Color(0x22FFFFFF), Offset(0f, mid), Offset(w, mid), 1f)
            if (xe > xs) {
                drawRoundRect(col.copy(alpha = 0.28f), Offset(xs, 2f), Size(xe - xs, h - 4f), CornerRadius(5f, 5f))
                val wave = col.copy(alpha = 1f).let { Color(
                    (it.red + 1f) / 2f, (it.green + 1f) / 2f, (it.blue + 1f) / 2f) }
                var px = max(0, xs.toInt())
                while (px < xe.toInt() + 1 && px < w) {
                    val s0 = ((px + sc) * spp).toInt()
                    if (s0 >= d.size) break
                    val s1 = (((px + 1 + sc) * spp).toInt()).coerceIn(s0 + 1, d.size)
                    val step = max(1, (s1 - s0) / 24)
                    var mn = 1f; var mx = -1f; var k = s0
                    while (k < s1) { val v = d[k]; if (v < mn) mn = v; if (v > mx) mx = v; k += step }
                    val y1 = mid - mx.coerceIn(-1f, 1f) * mid * 0.9f
                    val y2 = mid - mn.coerceIn(-1f, 1f) * mid * 0.9f
                    drawLine(wave, Offset(px + 0.5f, y1), Offset(px + 0.5f, max(y2, y1 + 1f)), 1f)
                    px++
                }
            }
            val r = st.sel()
            if (r != null && active) {
                val xa = r.first / spp - sc; val xb = r.last / spp - sc
                drawRect(Color(0x44FFFFFF), Offset(xa, 0f), Size(xb - xa, h))
            }
            val hx = st.head / spp - sc
            if (hx in 0f..w) drawLine(Color.White, Offset(hx, 0f), Offset(hx, h), 2f)
        }
    }
}

@Composable
fun AudacityApp(st: AppState, onRecord: () -> Unit, onImport: () -> Unit, onExport: () -> Unit) {
    var viewW by remember { mutableFloatStateOf(1f) }
    val hx = st.head / st.spp - st.scrollPx
    if ((st.playing || st.recording) && hx > viewW) st.scrollPx = st.head / st.spp - viewW * 0.1f

    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding()) {
        // Menús (como la barra de menú de escritorio)
        Row(Modifier.fillMaxWidth().background(Panel).horizontalScroll(rememberScrollState())) {
            MenuBtn("Archivo", listOf(
                "Nuevo proyecto" to { st.newProject() },
                "Importar audio (WAV)" to onImport,
                "Exportar como WAV" to onExport))
            MenuBtn("Editar", listOf(
                "Deshacer" to { st.undo() }, "Rehacer" to { st.redo() },
                "Cortar" to { st.cut() }, "Copiar" to { st.copy() }, "Pegar" to { st.paste() },
                "Eliminar" to { st.delete() }, "Silenciar selección" to { st.silence() },
                "Seleccionar todo" to { st.selectAll() }))
            MenuBtn("Pista", listOf("Añadir pista" to { st.addTrack() }, "Eliminar pista" to { st.removeTrack() }))
            MenuBtn("Generar", listOf("Tono 440 Hz (1 s)" to { st.tone() }))
            MenuBtn("Efecto", listOf(
                "Amplificar +3 dB" to { st.amplify(3f) }, "Reducir −3 dB" to { st.amplify(-3f) },
                "Normalizar" to { st.normalize() }, "Invertir" to { st.reverse() },
                "Invertir polaridad" to { st.invert() },
                "Fundido de entrada" to { st.fadeIn() }, "Fundido de salida" to { st.fadeOut() }))
            MenuBtn("Ayuda", listOf("Acerca de" to { st.status = "Audacity 4 Android 4.0.0 · réplica de la interfaz de Audacity 4" }))
        }
        // Transporte
        Row(
            Modifier.fillMaxWidth().background(Panel2).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            TBtn("pause") { st.stop() }
            TBtn("play", st.playing) { st.play() }
            TBtn("stop") { st.stop() }
            TBtn("start") { st.head = 0; st.scrollPx = 0f }
            TBtn("end") { st.head = st.totalLen().toLong() }
            TBtn("rec", st.recording, onRecord)
            Spacer(Modifier.weight(1f))
            Text(fmtClock(st.head / SR.toDouble()), color = Fg, fontSize = 17.sp,
                modifier = Modifier.background(Color(0xFF14161B), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 6.dp))
        }
        // Herramientas
        Row(
            Modifier.fillMaxWidth().background(Panel).horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SmallBtn("↶") { st.undo() }
            SmallBtn("↷") { st.redo() }
            SmallBtn("✂ Cortar") { st.cut() }
            SmallBtn("Copiar") { st.copy() }
            SmallBtn("Pegar") { st.paste() }
            SmallBtn("Zoom +") { st.zoom(0.5f) }
            SmallBtn("Zoom −") { st.zoom(2f) }
            SmallBtn("Ajustar") { st.fit(viewW) }
        }
        // Regla de tiempo
        Row(Modifier.fillMaxWidth().height(28.dp)) {
            Spacer(Modifier.width(HeaderW).fillMaxHeight().background(Panel))
            Canvas(
                Modifier.weight(1f).fillMaxHeight().background(Panel)
                    .pointerInput(Unit) { detectHorizontalDragGestures { ch, dx -> ch.consume(); st.scrollPx = (st.scrollPx - dx).coerceAtLeast(0f) } }
                    .pointerInput(Unit) { detectTapGestures { o -> st.head = st.xToSample(o.x) } }
            ) {
                val pxSec = SR / st.spp; val sc = st.scrollPx
                val steps = doubleArrayOf(0.01, 0.02, 0.05, 0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 15.0, 30.0, 60.0, 120.0, 300.0, 600.0)
                val iv = steps.firstOrNull { it * pxSec >= 80 } ?: 1800.0
                val paint = android.graphics.Paint().apply { color = 0xFFB0B6C4.toInt(); textSize = 24f; isAntiAlias = true }
                var t = floor((sc / pxSec) / iv) * iv
                while (true) {
                    val x = (t * pxSec - sc).toFloat()
                    if (x > size.width) break
                    if (x >= 0f) {
                        drawLine(Color(0xFF8A90A0), Offset(x, size.height * 0.55f), Offset(x, size.height), 1f)
                        val m = (t / 60).toInt(); val s = t - m * 60
                        val txt = if (iv < 1.0) "%d:%05.2f".format(m, s) else "%d:%02d".format(m, s.toInt())
                        drawContext.canvas.nativeCanvas.drawText(txt, x + 4f, size.height * 0.5f, paint)
                    }
                    t += iv
                }
            }
        }
        // Pistas
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            st.tracks.forEachIndexed { i, t -> TrackRow(st, i, t) { viewW = it } }
            if (st.tracks.isEmpty()) {
                Text("Proyecto vacío. Pulsa ● para grabar, o usa Archivo → Importar audio.",
                    color = Dim, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            }
        }
        // Desplazamiento horizontal
        val maxScroll = max(1f, st.totalLen() / st.spp)
        Slider(
            value = st.scrollPx.coerceIn(0f, maxScroll), onValueChange = { st.scrollPx = it },
            valueRange = 0f..maxScroll, modifier = Modifier.padding(horizontal = 12.dp)
        )
        // Barra de estado
        val r = st.sel()
        val selTxt = if (r != null) "Selección: ${fmtClock(r.first / SR.toDouble())} – ${fmtClock(r.last / SR.toDouble())}" else st.status
        Text(selTxt, color = Dim, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().background(Panel).padding(8.dp))
    }
}
