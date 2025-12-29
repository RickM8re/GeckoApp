package re.rickmoo.gecko.service.update

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import re.rickmoo.gecko.BuildConfig
import re.rickmoo.gecko.datasource.Preferences
import re.rickmoo.gecko.misc.AppStatus
import re.rickmoo.gecko.misc.UpdateBus


class AppUpdateService : LifecycleService() {

    private val preferences by lazy { Preferences(this) }

    companion object {
        const val TAG = "AppUpdateService"
        private val DEBUG = BuildConfig.DEBUG
        private val RELEASE_CHANNEL = BuildConfig.VERSION_NAME.startsWith("v")
        private val RELEASE_SERVICE_ROOT =
            "${BuildConfig.UPDATE_BASE_URL.trimEnd('/')}/${BuildConfig.GIT_BRANCH}"
        private val NIGHTLY_SERVICE_ROOT =
            "${BuildConfig.UPDATE_BASE_URL.trimEnd('/')}/${BuildConfig.GIT_BRANCH}/nightly"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        // 启动检查任务
        checkForUpdates()

        // 如果只是检查一次就结束，可以使用 START_NOT_STICKY
        return START_NOT_STICKY
    }

    private fun checkForUpdates() {
        lifecycleScope.launch {
            try {

                val config = withContext(Dispatchers.IO) {
                    val url =
                        if (preferences[Preferences.App.UPDATE_CHANNEL]?.let { it == "release" } ?: RELEASE_CHANNEL) {
                            "${RELEASE_SERVICE_ROOT}/latest-${BuildConfig.BUILD_TYPE}.json"
                        } else {
                            "${NIGHTLY_SERVICE_ROOT}/latest-${BuildConfig.BUILD_TYPE}.json"
                        }
                    UpdateInfoApi.INSTANCE.getUpdateInfo(url)
                }

                // 检查版本号
                val currentVersionCode = getAppVersionCode()
                if (config.versionCode > currentVersionCode) {
                    Log.i(TAG, "发现新版本: ${config.versionName}")

                    // 解析最佳下载链接
                    val downloadFileName = resolveDownloadFileName(config)

                    if (downloadFileName != null) {
                        Log.i(TAG, "匹配到的APK: $downloadFileName")
                        val base = if (RELEASE_CHANNEL) {
                            "${RELEASE_SERVICE_ROOT}/${config.version}/${if (DEBUG) "debug" else "release"}"
                        } else {
                            "${NIGHTLY_SERVICE_ROOT}/${config.version}/${if (DEBUG) "debug" else "release"}"
                        }
                        handleUpdateEvent(
                            UpdateInfoBusCarrier(
                                "$base/$downloadFileName",
                                config.changeLog?.let { "$base/$it" },
                                config
                            )
                        )
                    } else {
                        Log.w(TAG, "未找到匹配当前设备架构的安装包")
                    }
                } else {
                    Log.i(TAG, "当前已是最新版本")
                }

            } catch (e: Exception) {
                Log.e(TAG, "检查更新失败", e)
            } finally {
                // 检查完成后停止服务
                stopSelf()
            }
        }
    }

    private suspend fun handleUpdateEvent(info: UpdateInfoBusCarrier) {
        if (AppStatus.isAppForeground()) {
            Log.d(TAG, "App在前台，发送事件给Activity")
            UpdateBus.emitUpdate(info)
        } else {
            Log.d(TAG, "App在后台，发送Notification")
//            showNotification(info)
        }
    }

    private fun getAppVersionCode(): Int {
        return try {
            PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(packageName, 0)).toInt()
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Get Version Code error", e)
            0
        }
    }

    private fun resolveDownloadFileName(config: UpdateIndexModel): String? {
        // 1. 获取当前 App 是 Debug 还是 Release

        // 获取对应构建类型的 Map (Map<Abi, Url>)
        val abiMap = config.apks

        if (abiMap.isNullOrEmpty()) return null

        // 2. 获取设备支持的 ABI 列表
        // Build.SUPPORTED_ABIS 会按优先级返回设备支持的架构，例如 ["arm64-v8a", "armeabi-v7a", "armeabi"]
        val deviceAbis = Build.SUPPORTED_ABIS

        // 3. 遍历设备支持的 ABI，查找服务端是否有对应资源
        for (abi in deviceAbis) {
            if (abiMap.containsKey(abi)) {
                Log.i(TAG, "匹配到最佳 ABI: $abi")
                return abiMap[abi]
            }
        }

        // 4. 兜底方案：如果找不到特定架构，尝试查找 "universal" (通用包) 或默认值
        if (abiMap.containsKey("noarch")) {
            Log.d(TAG, "使用通用架构包 (noarch)")
            return abiMap["noarch"]
        }

        return null
    }
}