package kz.moyamuzyka.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import kz.moyamuzyka.app.dsp.DspHub
import kz.moyamuzyka.app.ui.AppRoot
import kz.moyamuzyka.app.ui.MoyaTheme

class MainActivity : ComponentActivity() {
    private lateinit var conn: PlayerConnection
    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DspHub.init(this)
        Library.load(this)
        conn = PlayerConnection(this)
        conn.connect()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { MoyaTheme { AppRoot(conn) } }
    }

    override fun onDestroy() {
        conn.release()
        super.onDestroy()
    }
}
