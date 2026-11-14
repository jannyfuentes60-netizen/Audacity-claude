package com.pocoyin.audacity

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.net.Uri
import androidx.compose.runtime.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

const val SR = 44100

class Track(var name: String, var data: FloatArray, var color: Int) {
    var mute = false
    var solo = false
    var gain = 1f
    fun dup(): Track { val t = Track(name, data, color); t.mute = mute; t.solo = solo; t.gain = gain; return t }
}

class AppState {
    val tracks = mutableStateListOf<Track>()
    var rev by mutableIntStateOf(0)
    var selA by mutableLongStateOf(-1L)
    var selB by mutableLongStateOf(-1L)
    var head by mutableLongStateOf(0L)
    var spp by mutableFloatStateOf(200f)
    var scrollPx by mutableFloatStateOf(0f)
    var playing by mutableStateOf(false)
    var recording by mutableStateOf(false)
    var activeTrack by mutableIntStateOf(0)
    var status by mutableStateOf("Audacity 4 para Android")

    private var clip: FloatArray? = null
    private val undoStack = ArrayList<List<Track>>()
    private val redoStack = ArrayList<List<Track>>()
    @Volatile private var stopFlag = false

    fun xToSample(x: Float): Long = ((x + scrollPx) * spp).toLong().coerceAtLeast(0L)
    fun totalLen(): Int = tracks.maxOfOrNull { it.data.size } ?: 0
    fun cur(): Track? = tracks.getOrNull(activeTrack)
    fun sel(): LongRange? {
        if (selA < 0) return null
        val a = min(selA, selB); val b = max(selA, selB)
        return if (b > a) a..b else null
    }

    private fun snap() {
        undoStack.add(tracks.map { it.dup() })
        if (undoStack.size > 30) undoStack.removeAt(0)
        redoStack.clear()
    }
    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(tracks.map { it.dup() })
        val s = undoStack.removeAt(undoStack.size - 1)
        tracks.clear(); tracks.addAll(s); rev++
    }
    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(tracks.map { it.dup() })
        val s = redoStack.removeAt(redoStack.size - 1)
        tracks.clear(); tracks.addAll(s); rev++
    }

    fun newProject() {
        stop(); snap(); tracks.clear(); head = 0; selA = -1; selB = -1; scrollPx = 0f; activeTrack = 0; rev++
    }
    fun addTrack() {
        snap(); tracks.add(Track("Pista ${tracks.size + 1}", FloatArray(0), tracks.size))
        activeTrack = tracks.size - 1; rev++
    }
    fun removeTrack() {
        val t = cur() ?: return
        snap(); tracks.remove(t); activeTrack = 0; rev++
    }

    // ---------- Edición ----------
    fun copy() {
        val t = cur() ?: return; val r = sel() ?: return
        val a = r.first.toInt().coerceIn(0, t.data.size); val b = r.last.toInt().coerceIn(0, t.data.size)
        clip = t.data.copyOfRange(a, b); status = "Copiado"
    }
    fun delete() {
        val t = cur() ?: return; val r = sel() ?: return
        val a = r.first.toInt().coerceIn(0, t.data.size); val b = r.last.toInt().coerceIn(0, t.data.size)
        snap(); t.data = t.data.copyOfRange(0, a) + t.data.copyOfRange(b, t.data.size)
        selA = -1; head = a.toLong(); rev++
    }
    fun cut() { copy(); delete() }
    fun paste() {
        val c = clip ?: return; val t = cur() ?: return
        val p = (sel()?.first ?: head).toInt().coerceIn(0, t.data.size)
        snap(); t.data = t.data.copyOfRange(0, p) + c + t.data.copyOfRange(p, t.data.size)
        head = (p + c.size).toLong(); selA = -1; rev++
    }
    fun selectAll() { val t = cur() ?: return; selA = 0; selB = t.data.size.toLong() }

    private fun applyRange(onlySel: Boolean = false, f: (FloatArray, Int, Int) -> Unit) {
        val t = cur() ?: return
        val r = sel()
        if (onlySel && r == null) { status = "Selecciona una parte del audio"; return }
        snap()
        val d = t.data.copyOf()
        val a = (r?.first ?: 0L).toInt().coerceIn(0, d.size)
        val b = (r?.last ?: d.size.toLong()).toInt().coerceIn(0, d.size)
        f(d, a, b); t.data = d; rev++
    }
    fun amplify(db: Float) {
        val g = 10f.pow(db / 20f)
        applyRange { d, a, b -> for (i in a until b) d[i] = (d[i] * g).coerceIn(-1f, 1f) }
    }
    fun normalize() = applyRange { d, a, b ->
        var p = 0f; for (i in a until b) p = max(p, abs(d[i]))
        if (p > 0f) { val g = 0.89f / p; for (i in a until b) d[i] *= g }
    }
    fun reverse() = applyRange { d, a, b -> d.reverse(a, b) }
    fun invert() = applyRange { d, a, b -> for (i in a until b) d[i] = -d[i] }
    fun silence() = applyRange(true) { d, a, b -> for (i in a until b) d[i] = 0f }
    fun fadeIn() = applyRange { d, a, b -> val n = max(1, b - a); for (i in a until b) d[i] *= (i - a).toFloat() / n }
    fun fadeOut() = applyRange { d, a, b -> val n = max(1, b - a); for (i in a until b) d[i] *= 1f - (i - a).toFloat() / n }
    fun tone() {
        val t = cur() ?: run { addTrack(); cur()!! }
        snap()
        val p = head.toInt().coerceIn(0, t.data.size)
        val n = SR
        val s = FloatArray(n) { (0.5 * sin(2.0 * PI * 440.0 * it / SR)).toFloat() }
        t.data = t.data.copyOfRange(0, p) + s + t.data.copyOfRange(p, t.data.size); rev++
    }

    // ---------- Vista ----------
    fun zoom(f: Float) {
        val old = spp; spp = (spp * f).coerceIn(1f, 20000f)
        scrollPx = (scrollPx * old / spp).coerceAtLeast(0f)
    }
    fun fit(w: Float) {
        val n = totalLen()
        if (n > 0 && w > 1f) { spp = (n / w).coerceAtLeast(1f); scrollPx = 0f }
    }

    // ---------- Reproducción ----------
    private fun mixAt(ts: List<Track>, anySolo: Boolean, i: Int): Float {
        var s = 0f
        for (t in ts) {
            if (t.mute || (anySolo && !t.solo)) continue
            val d = t.data
            if (i < d.size) s += d[i] * t.gain
        }
        return s.coerceIn(-1f, 1f)
    }

    fun play() {
        if (playing || recording || tracks.isEmpty()) return
        val r = sel()
        var pos = (r?.first ?: head).toInt()
        val end = (r?.last ?: totalLen().toLong()).toInt()
        if (pos >= end) pos = 0
        val ts = tracks.toList()
        val anySolo = ts.any { it.solo }
        stopFlag = false; playing = true
        Thread {
            val minBuf = AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val at = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(SR)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(minBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM).build()
            at.play()
            val buf = ShortArray(2048)
            while (!stopFlag && pos < end) {
                val n = min(buf.size, end - pos)
                for (i in 0 until n) buf[i] = (mixAt(ts, anySolo, pos + i) * 32767f).toInt().toShort()
                at.write(buf, 0, n)
                pos += n; head = pos.toLong()
            }
            at.stop(); at.release(); playing = false
        }.start()
    }

    fun stop() { stopFlag = true; recording = false }

    @SuppressLint("MissingPermission")
    fun startRecord() {
        if (recording || playing) return
        snap()
        val t = Track("Pista ${tracks.size + 1}", FloatArray(0), tracks.size)
        tracks.add(t); activeTrack = tracks.size - 1
        head = 0; recording = true
        Thread {
            val minBuf = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val ar = AudioRecord(MediaRecorder.AudioSource.MIC, SR, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, minBuf * 2)
            val b = ShortArray(2048)
            var acc = FloatArray(SR * 10); var n = 0; var k = 0
            ar.startRecording()
            while (recording) {
                val r = ar.read(b, 0, b.size)
                if (r > 0) {
                    if (n + r > acc.size) acc = acc.copyOf(acc.size * 2)
                    for (i in 0 until r) acc[n + i] = b[i] / 32768f
                    n += r; head = n.toLong()
                    if (++k % 3 == 0) { t.data = acc.copyOf(n); rev++ }
                }
            }
            ar.stop(); ar.release()
            t.data = acc.copyOf(n); rev++
        }.start()
    }
    fun toggleRecord() { if (recording) stop() else startRecord() }

    // ---------- Archivos ----------
    fun importWav(cr: ContentResolver, uri: Uri) {
        try {
            val b = cr.openInputStream(uri)!!.use { it.readBytes() }
            val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
            var p = 12; var ch = 1; var sr = SR; var bits = 16; var ds = -1; var dl = 0
            while (p + 8 <= b.size) {
                val id = String(b, p, 4); val sz = bb.getInt(p + 4)
                if (id == "fmt ") { ch = bb.getShort(p + 10).toInt(); sr = bb.getInt(p + 12); bits = bb.getShort(p + 22).toInt() }
                else if (id == "data") { ds = p + 8; dl = min(sz, b.size - ds); break }
                p += 8 + sz + (sz and 1)
            }
            if (ds < 0 || bits != 16 || ch < 1) { status = "Por ahora solo WAV PCM de 16 bits"; return }
            val frames = dl / (2 * ch)
            val mono = FloatArray(frames) { i ->
                var s = 0f
                for (c in 0 until ch) s += bb.getShort(ds + (i * ch + c) * 2) / 32768f
                s / ch
            }
            val out = if (sr == SR || frames < 2) mono else {
                val n = (frames.toLong() * SR / sr).toInt()
                FloatArray(n) { i ->
                    val x = i.toDouble() * sr / SR
                    val i0 = x.toInt().coerceAtMost(frames - 1); val i1 = (i0 + 1).coerceAtMost(frames - 1)
                    val f = (x - i0).toFloat(); mono[i0] * (1 - f) + mono[i1] * f
                }
            }
            snap(); tracks.add(Track("Audio importado", out, tracks.size))
            activeTrack = tracks.size - 1; rev++
            status = "Importado (${out.size / SR} s)"
        } catch (e: Exception) { status = "Error al importar: ${e.message}" }
    }

    fun exportWav(cr: ContentResolver, uri: Uri) {
        try {
            val ts = tracks.toList(); val n = totalLen(); val solo = ts.any { it.solo }
            val bb = ByteBuffer.allocate(44 + n * 2).order(ByteOrder.LITTLE_ENDIAN)
            bb.put("RIFF".toByteArray()); bb.putInt(36 + n * 2); bb.put("WAVE".toByteArray())
            bb.put("fmt ".toByteArray()); bb.putInt(16); bb.putShort(1); bb.putShort(1)
            bb.putInt(SR); bb.putInt(SR * 2); bb.putShort(2); bb.putShort(16)
            bb.put("data".toByteArray()); bb.putInt(n * 2)
            for (i in 0 until n) bb.putShort((mixAt(ts, solo, i) * 32767f).toInt().toShort())
            cr.openOutputStream(uri)!!.use { it.write(bb.array()) }
            status = "Exportado correctamente"
        } catch (e: Exception) { status = "Error al exportar: ${e.message}" }
    }
}
