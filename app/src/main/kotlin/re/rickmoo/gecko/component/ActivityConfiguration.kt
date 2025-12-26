package re.rickmoo.gecko.component

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.json.JSONObject
import re.rickmoo.gecko.service.update.AppUpdateService

class ActivityConfiguration(private val activity: Activity) {

    fun setSystemUiVisible(visibility: Boolean) {
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            if (visibility) {
                show(WindowInsetsCompat.Type.systemBars())
                show(WindowInsetsCompat.Type.systemGestures())
            } else {
                hide(WindowInsetsCompat.Type.systemBars())
                hide(WindowInsetsCompat.Type.systemGestures())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    fun edgeToEdge(statusBarStyle: JSONObject?, navigationBarStyle: JSONObject?) {
        (activity as? ComponentActivity)?.let {
            it.enableEdgeToEdge(
                statusBarStyle?.let {
                    SystemBarStyle.auto(it.getInt("lightScrim"), it.getInt("darkScrim"))
                } ?: SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
                navigationBarStyle?.let {
                    SystemBarStyle.auto(it.getInt("lightScrim"), it.getInt("darkScrim"))
                } ?: SystemBarStyle.auto(
                    Color.argb(0xe6, 0xFF, 0xFF, 0xFF),
                    Color.argb(0x80, 0x1b, 0x1b, 0x1b)
                ),
            )
        }
    }

    fun startUpdateService() {
        val intent = Intent(activity, AppUpdateService::class.java)
        activity.startService(intent)
    }
}