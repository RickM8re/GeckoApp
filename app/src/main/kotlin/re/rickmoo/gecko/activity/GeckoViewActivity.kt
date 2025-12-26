package re.rickmoo.gecko.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import re.rickmoo.gecko.component.ActivityConfiguration
import re.rickmoo.gecko.component.GeckoBridge
import re.rickmoo.gecko.infra.ActivityBridge
import re.rickmoo.gecko.infra.GeckoConfigurer
import re.rickmoo.gecko.infra.GetContentWithMimeTypes
import re.rickmoo.gecko.infra.GetMultipleContentWithMimeTypes
import re.rickmoo.gecko.service.update.AppUpdateService

class WebViewActivity : ComponentActivity(), ActivityBridge {
    @Volatile
    private var prepared = false
    private val session = GeckoSession()

    override fun onDestroy() {
        super.onDestroy()
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
            addExtensionDependency(GeckoBridge())
            addExtensionDependency(ActivityConfiguration(this@WebViewActivity))
            registerWebExtension()
            addProgressDelegate {
                prepared = true
            }
        }
        configurer.load("https://www.bilibili.com")
        setContent {
            re.rickmoo.gecko.compose.GeckoView(geckoView, lifecycle)
        }
        startUpdateService()
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

