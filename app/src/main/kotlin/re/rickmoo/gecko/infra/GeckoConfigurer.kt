package re.rickmoo.gecko.infra

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.startup.AppInitializer
import org.json.JSONObject
import org.mozilla.gecko.util.ThreadUtils.runOnUiThread
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.MediaCallback
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.full.memberFunctions


class GeckoConfigurer(
    private val context: Context,
    private val activityRequestable: ActivityRequestable,
    geckoView: GeckoView,
    private val session: GeckoSession,
    private val configure: GeckoConfigurer.(session: GeckoSession) -> Unit,
) {
    private val geckoRuntime =
        AppInitializer.getInstance(context).initializeComponent(GeckoRuntimeInitializer::class.java)

    init {
        session.open(geckoRuntime)
        geckoView.setSession(session)
        session.settings.allowJavascript = true
    }

    private val dependency by lazy { ConcurrentHashMap<String, Any>() }

    fun addExtensionDependency(name: String, dependency: Any) {
        this.dependency[name] = dependency
    }

    fun addExtensionDependency(dependency: Any) {
        this.dependency[dependency::class.simpleName!!.replaceFirstChar { it.lowercase() }] = dependency
    }

    fun load(uri: String) {
        configure(session)
        session.loadUri(uri)
    }


    private lateinit var permissionCallbackInternal: (Boolean) -> Unit
    private lateinit var multiplePermissionCallbackInternal: (Map<String, Boolean>) -> Unit
    private lateinit var contentCallbackInternal: (Uri?) -> Unit
    private lateinit var multipleContentCallbackInternal: (List<Uri>) -> Unit
    private lateinit var activityResultCallbackInternal: (ActivityResult) -> Unit

    fun permissionCallback(granted: Boolean) {
        permissionCallbackInternal(granted)
    }

    fun multiplePermissionCallback(granted: Map<String, Boolean>) {
        multiplePermissionCallbackInternal(granted)
    }

    fun contentCallback(content: Uri?) {
        contentCallbackInternal(content)
    }

    fun multipleContentCallback(content: List<Uri>) {
        multipleContentCallbackInternal(content)
    }

    fun activityResultCallback(result: ActivityResult) {
        activityResultCallbackInternal(result)
    }


    fun addMediaPermissionRequest() {
        session.setPermissionDelegate(
            object : PermissionDelegate {
                override fun onMediaPermissionRequest(
                    session: GeckoSession,
                    uri: String,
                    video: Array<out PermissionDelegate.MediaSource?>?,
                    audio: Array<out PermissionDelegate.MediaSource?>?,
                    callback: MediaCallback
                ) {
                    val audioPermission = audio?.isNotEmpty() == true && ContextCompat.checkSelfPermission(
                        context, Manifest.permission.RECORD_AUDIO
                    ) != PERMISSION_GRANTED
                    val videoPermission = video?.isNotEmpty() == true
                        && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PERMISSION_GRANTED

                    if (audioPermission && !videoPermission) {
                        permissionCallbackInternal = {
                            callback.grant(video?.firstOrNull(), if (it) audio.firstOrNull() else null)
                        }
                        activityRequestable.requestPermission(Manifest.permission.RECORD_AUDIO)
                    } else if (!audioPermission && videoPermission) {
                        permissionCallbackInternal = {
                            callback.grant(if (it) video.firstOrNull() else null, audio?.firstOrNull())
                        }
                        activityRequestable.requestPermission(Manifest.permission.CAMERA)
                    } else if (audioPermission) {
                        multiplePermissionCallbackInternal = {
                            callback.grant(
                                if (it[Manifest.permission.CAMERA] == true) video?.firstOrNull() else null,
                                if (it[Manifest.permission.RECORD_AUDIO] == true) audio.firstOrNull() else null,
                            )
                        }
                        activityRequestable.requestPermission(
                            arrayOf(
                                Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.CAMERA
                            )
                        )
                    } else {
                        callback.grant(video?.firstOrNull(), audio?.firstOrNull())
                    }
                }

                override fun onAndroidPermissionsRequest(
                    session: GeckoSession,
                    permissions: Array<out String?>?,
                    callback: PermissionDelegate.Callback
                ) {
                    val needGrant = permissions?.filter {
                        if (it == null) false
                        else if (ContextCompat.checkSelfPermission(context, it) != PERMISSION_GRANTED) {
                            true
                        } else
                            false
                    }?.filterNotNull()
                    if (needGrant.isNullOrEmpty()) callback.grant()
                    else {
                        activityRequestable.requestPermission(needGrant.toTypedArray())
                        multiplePermissionCallbackInternal = {
                            callback.grant()
                        }
                    }
                }
            })

    }

    fun addPromptDelegate() {
        session.promptDelegate = object : GeckoSession.PromptDelegate {
            override fun onFilePrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.FilePrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                when (prompt.type) {
                    GeckoSession.PromptDelegate.FilePrompt.Type.SINGLE -> {
                        contentCallbackInternal = {
                            if (it != null)
                                result.complete(prompt.confirm(context, it))
                            else
                                result.complete(prompt.dismiss())
                        }
                        activityRequestable.requestContent(prompt.mimeTypes?.filterNotNull()?.toTypedArray() ?: emptyArray())
                    }

                    GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE -> {
                        multipleContentCallbackInternal = {
                            if (it.isNotEmpty())
                                result.complete(prompt.confirm(context, it.toTypedArray()))
                            else
                                result.complete(prompt.dismiss())
                        }
                        activityRequestable.requestMultipleContent(
                            prompt.mimeTypes?.filterNotNull()?.toTypedArray() ?: emptyArray()
                        )
                    }

                    GeckoSession.PromptDelegate.FilePrompt.Type.FOLDER -> {
                        activityResultCallbackInternal = {
                            if (it.resultCode == Activity.RESULT_OK && it.data != null) {
                                val uri: Uri? = it.data?.data
                                if (uri != null) {
                                    result.complete(prompt.confirm(context, uri))
                                } else {
                                    result.complete(prompt.dismiss())
                                }
                            }
                        }
                    }
                }
                return result
            }
        }
    }

    fun addNavigationDelegate(
        onHrefChange: ((String) -> Unit)? = null,
        onCanBack: ((Boolean) -> Unit)? = null,
        onCanForward: ((Boolean) -> Unit)? = null,
    ) {
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<PermissionDelegate.ContentPermission?>,
                hasUserGesture: Boolean
            ) {
                if (url.isNullOrEmpty()) return
                if (url.startsWith("about:")) return
                onHrefChange?.invoke(url)
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                onCanBack?.invoke(canGoBack)
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                onCanForward?.invoke(canGoForward)
            }

            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny> {

                val url = request.uri

                if (url.startsWith("http://") || url.startsWith("https://") ||
                    url.startsWith("file://") || url.startsWith("about:")
                ) {
                    return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                }

                return try {
                    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                    context.startActivity(intent)
                    GeckoResult.fromValue(AllowOrDeny.DENY)

                } catch (e: Exception) {
                    e.printStackTrace()

                    Toast.makeText(context, "未安装相关应用", Toast.LENGTH_SHORT).show()
                    GeckoResult.fromValue(AllowOrDeny.DENY)
                }
            }
        }

    }

    fun addProgressDelegate(onComplete: (success: Boolean) -> Unit) {
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                onComplete(success)
            }
        }
    }

    fun registerWebExtension() {
        geckoRuntime.webExtensionController.ensureBuiltIn(
            "resource://android/assets/messaging/", "me@rickmoo.re"
        ).accept({ extension ->
            dependency.forEach { (name, _) ->
                runOnUiThread {
                    extension?.setMessageDelegate(object : WebExtension.MessageDelegate {
                        // stream message
                        override fun onConnect(port: WebExtension.Port) {
                            Log.i(null, "onConnect: port name:${port.name}")
                            val portableApp = dependency[name] as? Portable
                            if (portableApp == null) {
                                port.disconnect()
                                Log.i(null, "app: $name does not support port connection")
                                return
                            }
                            portableApp.withPort(port)
                        }

                        // One time message
                        override fun onMessage(
                            nativeApp: String,
                            message: Any, // JSONObject | primitive type
                            sender: WebExtension.MessageSender,
                        ): GeckoResult<in Any> {
                            val result = GeckoResult<Any>()
                            val app = dependency[nativeApp]
                            if (app == null) {
                                result.completeExceptionally(RuntimeException("Cannot dispatch native call"))
                                return result
                            }
                            when (message) {
                                is JSONObject -> {
                                    val action = message.getString("action")
                                    val data = message.get("data")
                                    val function = app::class.memberFunctions.find { it.name == action }
                                    if (function == null) {
                                        result.completeExceptionally(RuntimeException("native function not found: $action"))
                                        return result
                                    }
                                    try {
                                        val value = function.callNative(app, data)
                                        if (value is GeckoResult<*>) {
                                            @Suppress("UNCHECKED_CAST")
                                            return value as GeckoResult<Any>
                                        }
                                        result.complete(value)
                                    } catch (e: Throwable) {
                                        Log.e("GeckoExtension", "call native failed", e)
                                        result.completeExceptionally(RuntimeException("Cannot dispatch native call", e))
                                    }
                                }

                                else -> {
                                    throw RuntimeException("Cannot dispatch native call")
                                }
                            }
                            return result
                        }
                    }, name)
                }
            }
        }, {
            Log.e("geckoView", "install webExtension failed", it)
        })
    }
}