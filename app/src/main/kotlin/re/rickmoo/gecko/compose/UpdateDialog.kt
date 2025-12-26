package re.rickmoo.gecko.compose

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Request
import re.rickmoo.gecko.misc.OkHttp
import re.rickmoo.gecko.service.update.UpdateInfoApi
import re.rickmoo.gecko.service.update.UpdateInfoBusCarrier
import java.io.File
import java.io.FileOutputStream


@Composable
fun UpdateDialog(
    versionInfo: UpdateInfoBusCarrier,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 获取屏幕配置以计算高度
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val mdUrl = versionInfo.changeLogUrl
    // 异步加载 Markdown 内容的状态管理
    // 如果 url 为 null，result 默认为 null；如果不为 null，则开始加载
    val markdownContentState = produceState(
        initialValue = if (mdUrl == null) LoadState.NotRequired else LoadState.Loading,
        key1 = mdUrl
    ) {
        if (mdUrl != null) {
            value = try {
                val content = withContext(Dispatchers.IO) {
                    UpdateInfoApi.INSTANCE.getUpdateMarkdown(mdUrl)
                }
                LoadState.Success(content)
            } catch (e: Exception) {
                LoadState.Error("加载更新日志失败: ${e.localizedMessage}")
            }
        }
    }
    var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }
    // 持有当前的 Call 对象以便取消
    var currentCall by remember { mutableStateOf<Call?>(null) }
    var pendingInstallFile by remember { mutableStateOf<File?>(null) }

    val installPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // 用户从设置页返回，再次检查权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val hasPermission = context.packageManager.canRequestPackageInstalls()
            if (hasPermission && pendingInstallFile != null) {
                installApk(context, pendingInstallFile!!)
                pendingInstallFile = null
            } else {
                // 用户依然拒绝，提示手动安装或无操作
                Toast.makeText(context, "需开启权限才能安装更新", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun checkAndInstall(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val hasPermission = context.packageManager.canRequestPackageInstalls()
            if (!hasPermission) {
                // 1. 没有权限，暂存文件
                pendingInstallFile = file
                // 2. 跳转到设置页面
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                installPermissionLauncher.launch(intent)
                return
            }
        }
        // 有权限或系统版本低，直接安装
        installApk(context, file)
    }

    fun startDownload() {
        downloadState = DownloadState.Downloading(0, 1)
        val base = File(context.cacheDir, "update") // 存放在 Cache 目录

        val client = OkHttp.client
        val request = Request.Builder().url(versionInfo.downloadUrl).build()
        val call = client.newCall(request)
        currentCall = call // 保存引用

        scope.launch(Dispatchers.IO) {
            try {
                val response = call.execute()
                if (!response.isSuccessful) throw Exception("服务器返回错误: ${response.code}")

                val body = response.body
                val totalLength = body.contentLength().coerceAtLeast(1)
                val inputStream = body.byteStream()
                base.mkdirs()
                val file = File(base, "update.apk")
                val outputStream = FileOutputStream(file)

                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                var totalBytesRead: Long = 0

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    // 更新进度
                    if (totalLength > 0) {
                        withContext(Dispatchers.Main) {
                            downloadState = DownloadState.Downloading(totalBytesRead, totalLength)
                        }
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                withContext(Dispatchers.Main) {
                    downloadState = DownloadState.Finished
                }
                checkAndInstall(file)

            } catch (e: Exception) {
                // 如果是用户主动取消，不做错误处理
                if (call.isCanceled()) {
                    withContext(Dispatchers.Main) { downloadState = DownloadState.Idle }
                } else {
                    withContext(Dispatchers.Main) {
                        downloadState = DownloadState.Error(e.localizedMessage ?: "下载失败")
                    }
                }
            }
        }
    }

    // --- 核心逻辑：取消下载 ---
    fun cancelDownload() {
        currentCall?.cancel()
        downloadState = DownloadState.Idle
        onDismiss()
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false) // 禁用默认宽度以允许自定义
    ) {
        Surface(
            modifier = Modifier
                // 平板适配：限制最大宽度，防止在平板上过宽
                .widthIn(max = 450.dp)
                .fillMaxWidth(0.9f) // 手机上占据 90% 宽度
                // 高度限制：40% - 75%
                .heightIn(min = screenHeight * 0.4f, max = screenHeight * 0.55f).height(IntrinsicSize.Min),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // --- 内容区域 (可滚动) ---
                Box(
                    modifier = Modifier
                        .weight(1f) // 占据剩余空间
                        .fillMaxWidth()
                ) {
                    ContentArea(
                        mdUrl = mdUrl,
                        versionInfo = versionInfo,
                        loadState = markdownContentState.value
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    // --- 底部按钮 ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                if (downloadState is DownloadState.Downloading) {
                                    cancelDownload() // 下载中点击：中断下载
                                } else {
                                    onDismiss() // 其他状态点击：关闭弹窗
                                }
                            }
                        ) {
                            Text(if (downloadState is DownloadState.Downloading) "取消下载" else "取消")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        if (downloadState is DownloadState.Idle || downloadState is DownloadState.Error) {
                            Button(onClick = { startDownload() }) {
                                Text("立即下载")
                            }
                        }

                    }
                    if (downloadState is DownloadState.Downloading) {
                        val (downloaded, total) = (downloadState as DownloadState.Downloading)
                        val progress = (downloadState as DownloadState.Downloading).let {
                            it.downloaded.toFloat() / it.total
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${displaySize(context, downloaded)}/${
                                displaySize(context, total)
                            } ${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }
        }
    }
}

fun displaySize(context: Context, sizeInBytes: Long): String {
    return Formatter.formatFileSize(context, sizeInBytes)
}

fun installApk(context: Context, file: File) {
    try {
        val intent = Intent(Intent.ACTION_VIEW)
        val uri: Uri
        // Android 7.0+ 需要 FileProvider
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // 注意：这里的 authority 必须与 AndroidManifest 中的 provider authorities 一致
            val authority = "re.rickmoo.gecko.install.fileprovider"
            uri = FileProvider.getUriForFile(context, authority, file)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            uri = Uri.fromFile(file)
        }

        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
        Log.e("InstallApk", "安装失败: ${e.message}")
    }
}

// 内部状态密封类
sealed class LoadState {
    object NotRequired : LoadState() // URL 为 null
    object Loading : LoadState()     // 正在加载
    data class Success(val content: String) : LoadState() // 加载成功
    data class Error(val msg: String) : LoadState() // 加载失败
}

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val downloaded: Long, val total: Long) : DownloadState() // 0.0 ~ 1.0
    object Finished : DownloadState()
    data class Error(val msg: String) : DownloadState()
}

@Composable
private fun ContentArea(
    mdUrl: String?,
    versionInfo: UpdateInfoBusCarrier,
    loadState: LoadState
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.verticalScroll(scrollState)
    ) {
        if (mdUrl == null) {
            // URL 为空的情况
            Text(
                text = "发现新版本: ${versionInfo.updateInfo.versionName}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "暂无详细更新日志。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // URL 不为空，根据加载状态显示
            when (loadState) {
                is LoadState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("正在加载更新信息...", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                is LoadState.Success -> {
                    // TODO: 这里如果需要渲染 Markdown，请使用第三方库，例如 `MarkdownText(markdown = loadState.content)`
                    MarkdownText(
                        modifier = Modifier.padding(top = 4.dp),
                        markdown = loadState.content,
                        style = TextStyle(
                            color = Color.Blue,
                            fontSize = 12.sp,
                            lineHeight = 10.sp,
                            textAlign = TextAlign.Justify,
                        )
                    )
                }

                is LoadState.Error -> {
                    Text(
                        text = loadState.msg,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "版本: ${versionInfo.updateInfo.versionName}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                LoadState.NotRequired -> {
                }
            }
        }
    }
}