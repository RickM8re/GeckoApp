package re.rickmoo.gecko.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import re.rickmoo.gecko.component.ActivityConfiguration
import re.rickmoo.gecko.component.GeckoBridge
import re.rickmoo.gecko.datasource.Preferences
import re.rickmoo.gecko.infra.ActivityBridge
import re.rickmoo.gecko.infra.GeckoConfigurer
import re.rickmoo.gecko.infra.GetContentWithMimeTypes
import re.rickmoo.gecko.infra.GetMultipleContentWithMimeTypes
import re.rickmoo.gecko.misc.AppStatus
import re.rickmoo.gecko.service.update.AppUpdateService

class WebViewActivity : ComponentActivity(), ActivityBridge {
    @Volatile
    private var prepared = false
    private val session = GeckoSession()
    private val preferences by lazy { Preferences(this) }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("RESTORE_URL", runBlocking { preferences[Preferences.GeckoView.RESTORE_URL] })
        outState.putString("ENV_ID", runBlocking { preferences[Preferences.GeckoView.ENV_ID] })
        super.onSaveInstanceState(outState)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !prepared }
        val geckoView = GeckoView(this)
        val configurer = GeckoConfigurer(this, this, geckoView, session) {
            multiplePermissionCallback = this::multiplePermissionCallback
            permissionCallback = this::permissionCallback
            contentCallback = this::contentCallback
            multipleContentCallback = this::multipleContentCallback
            activityResultCallback = this::activityResultCallback
            addPromptDelegate()
            addNavigationDelegate({
                lifecycleScope.launch { preferences[Preferences.GeckoView.RESTORE_URL] = it }
            })
            addExtensionDependency(GeckoBridge())
            addExtensionDependency(ActivityConfiguration(this@WebViewActivity))
            registerWebExtension()
            addProgressDelegate {
                prepared = true
            }
        }
        setContent {
            re.rickmoo.gecko.compose.GeckoView(geckoView, lifecycle)
        }
        val defaultUrl = runBlocking { preferences[Preferences.GeckoView.DEFAULT_URL] }
        val restoreUrl = runBlocking { preferences[Preferences.GeckoView.RESTORE_URL] }
        val envId = runBlocking { preferences[Preferences.GeckoView.ENV_ID] }
        var uri: String? = null
        if (savedInstanceState != null && envId != null) {
            savedInstanceState.getString("ENV_ID")?.let {
                if (envId == it) {
                    uri = restoreUrl ?: defaultUrl
                }
            }
        }
        if (uri == null && defaultUrl != null) {
            uri = defaultUrl
        } else {
            openHiddenConfig()
            return
        }
        configurer.load(uri)
    }

    override fun onStart() {
        super.onStart()
        if (AppStatus.isAppForeground()) {
            startUpdateService()
        }
    }

    fun openHiddenConfig() {
        val intent = Intent(this, HiddenConfigActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
            Intent.FLAG_ACTIVITY_NO_HISTORY
        startActivity(intent)
    }


    fun startUpdateService() {
        val intent = Intent(this, AppUpdateService::class.java)
        startService(intent)
    }

    // 多个权限申请
    private lateinit var multiplePermissionCallback: ((Map<String, Boolean>) -> Unit)
    private val multiplePermissionRegister =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            multiplePermissionCallback(it)
        }

    // 单个权限申请
    private lateinit var permissionCallback: ((Boolean) -> Unit)
    private val permissionRegister =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { permissionCallback(it) }

    // 获取内容
    private lateinit var contentCallback: (Uri?) -> Unit
    private val contentRegister = registerForActivityResult(GetContentWithMimeTypes()) { contentCallback(it) }

    private lateinit var multipleContentCallback: (List<Uri>) -> Unit
    private val multipleContentRegister =
        registerForActivityResult(GetMultipleContentWithMimeTypes()) { multipleContentCallback(it) }


    private lateinit var activityResultCallback: (ActivityResult) -> Unit
    private val activityResultRegister =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { activityResultCallback(it) }

    override fun requestPermission(permission: String) {
        permissionRegister.launch(permission)
    }

    override fun requestPermission(permission: Array<String>) {
        multiplePermissionRegister.launch(permission)
    }

    override fun requestContent(mimeTypes: Array<String>) {
        contentRegister.launch(mimeTypes)
    }

    override fun requestMultipleContent(mimeTypes: Array<String>) {
        multipleContentRegister.launch(mimeTypes)
    }

    override fun requestActivityResult(intent: Intent) {
        activityResultRegister.launch(intent)
    }
}

