package com.pocoyin.audacity

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme

class MainActivity : ComponentActivity() {
    private val st = AppState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val perm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok) st.startRecord() else st.status = "Permiso de micrófono denegado"
        }
        val imp = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) st.importWav(contentResolver, uri)
        }
        val exp = registerForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri ->
            if (uri != null) st.exportWav(contentResolver, uri)
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, surface = Panel)) {
                AudacityApp(
                    st,
                    onRecord = {
                        if (st.recording) st.stop()
                        else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) st.startRecord()
                        else perm.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onImport = { imp.launch(arrayOf("audio/*")) },
                    onExport = { exp.launch("proyecto.wav") }
                )
            }
        }
    }
}
