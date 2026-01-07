package re.rickmoo.gecko.component

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleCoroutineScope
import com.iflytek.aikit.core.AiAudio
import com.iflytek.aikit.core.AiHandle
import com.iflytek.aikit.core.AiHelper
import com.iflytek.aikit.core.AiListener
import com.iflytek.aikit.core.AiRequest
import com.iflytek.aikit.core.AiResponse
import com.iflytek.aikit.core.AiStatus
import com.iflytek.aikit.core.BaseLibrary
import com.iflytek.aikit.core.DataStatus
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension
import re.rickmoo.gecko.infra.Portable
import re.rickmoo.gecko.misc.AssetsHelper
import java.io.File
import kotlin.concurrent.thread


private const val TAG: String = "Iflytek"

const val ABILITY_ID = "ee62fa27c"
const val BUFFER_SIZE = 1280

class Iflytek
@RequiresPermission(Manifest.permission.RECORD_AUDIO)
constructor(
    private val context: Activity, private val lifecycleCoroutineScope: LifecycleCoroutineScope
) : Portable {

    val filesDir = File(context.filesDir, "iflytek")
    val workDir = File(filesDir, "aikit")

    fun init(appId: String, apiKey: String, apiSecret: String): GeckoResult<Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.setData(("package:" + context.packageName).toUri())
                context.startActivity(intent)
                return GeckoResult<Int>().apply {
                    completeExceptionally(Exception("Iflytek app is not granted permission."))
                }
            }
        }
        val perms = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE
        )
        for (p in perms) {
            val f = ContextCompat.checkSelfPermission(context, p)
            Log.d(TAG, String.format("%s - %d", p, f))
            if (f != PackageManager.PERMISSION_GRANTED) {
                context.requestPermissions(perms, 0XCF)
                break
            }
        }

        if (!(workDir.exists())) {
            workDir.mkdirs()
        }
        AssetsHelper.copyAssetsToDir(context, "CNENDictation", workDir)
        val params = BaseLibrary.Params.builder()
            .appId(appId) //"3a60b4f0"
            .apiKey(apiKey)//"7de0a6a07bf2aa67b5d9cbbeacad2150"
            .apiSecret(apiSecret)//"M2E0YzVlMmEzMDRlYTg4NjRjNTBkYThj"
            .workDir(workDir.absolutePath)
            .ability(ABILITY_ID)
            .authType(BaseLibrary.AuthType.DEVICE)
            .build()
        val inst = AiHelper.getInst()
        return GeckoResult<Int>().also {
            inst.registerListener { err, code ->
                if (code == 0) {
                    it.complete(code)
                    return@registerListener
                }
                it.completeExceptionally(RuntimeException("error in ${err.name}, code: $code"))
            }
            inst.init(context, params)
            inst.registerListener(ABILITY_ID, aiRespListener)
            val aiRequest = AiRequest.builder()
                .customText("PPROC_NOT_REP", File(workDir, "num_not_change_list").absolutePath, 0)
                .customText("PPROC_REPLACE", File(workDir, "replace_list").absolutePath, 0)
                .build()
            inst.loadData(ABILITY_ID, aiRequest)
        }

    }

    private var loadedNotRep = false
    private var loadedReplace = false
    fun loadCustomText(): Boolean {
        val aiRequest = AiRequest.builder()
            .customText("PPROC_NOT_REP", File(workDir, "num_not_change_list").absolutePath, 0)
            .customText("PPROC_REPLACE", File(workDir, "replace_list").absolutePath, 0)
            .build()
        return (AiHelper.getInst().loadData(ABILITY_ID, aiRequest) == 0).also {
            if (it) {
                loadedNotRep = true
                loadedReplace = true
            }
        }
    }

    fun unloadCustomText() {
        AiHelper.getInst().unLoadData("ee62fa27c", "PPROC_NOT_REP", 0)
        AiHelper.getInst().unLoadData("ee62fa27c", "PPROC_REPLACE", 1)
        loadedNotRep = false
        loadedReplace = false
    }


    @Volatile
    private var port: WebExtension.Port? = null

    @Volatile
    private var audioRecord: AudioRecord? = null

    @Volatile
    private var aiHandle: AiHandle? = null

    private val aiRespListener = object : AiListener {
        override fun onResult(handleID: Int, outputData: List<AiResponse>?, usrContext: Any?) {
            Log.i(TAG, "onResult:handleID:$handleID:${outputData?.size},usrContext:$usrContext")
            val end = outputData?.any { it.status == DataStatus.END.value } ?: false
            port?.postMessage(
                jsonResult(
                    "result",
                    outputData?.map { it.key to String(it.value, charset("GBK")) }?.toJsonObject()
                        ?.put("end", end)
                )
            )
        }

        override fun onEvent(handleID: Int, event: Int, eventData: List<AiResponse?>?, usrContext: Any?) {
            port?.postMessage(
                jsonResult(
                    "event", JSONObject()
                        .put("event", event)
                        .put("eventData", JSONArray().apply {
                            put(eventData?.map { it?.key to it?.value }?.toJsonObject())
                        })
                )
            )
            when (event) {
                0, 2, 3 -> {
                    port?.disconnect()
                    port = null
                }

                else -> {}
            }
        }

        override fun onError(handleID: Int, err: Int, msg: String?, usrContext: Any?) {
            port?.postMessage(jsonResult("error", "errorCode: $err, msg: $msg,"))
            port?.disconnect()
            port = null
        }

    }

    override fun withPort(port: WebExtension.Port) {
        if (this.port != null) {
            port.postMessage(
                jsonResult("error", "is listening")
            )
            port.disconnect()
        }
        this.port = port

        val param = AiRequest.builder()
            .param("lmLoad", true)
            .param("vadLoad", false)
            .param("puncLoad", true)
            .param("numLoad", true)
            .param("postprocOn", true)
            .param("lmOn", true)
            .param("vadOn", false)
            .param("vadLinkOn", false)
            .param("vadSpeechEnd", 80)
            .param("vadResponsetime", 1000)
            .param("dialectType", 0)
            .build()
//        val recorderHelperParams = RecorderHelper.Params.builder()
//            .build()
        thread(isDaemon = true) {
            aiHandle = AiHelper.getInst().start(ABILITY_ID, param, null)

//            aiHandle = AiHelper.getInst().record(ABILITY_ID,aiHandle,recorderHelperParams,param,ABILITY_ID,)
            if (aiHandle?.isFail == true) {
                port.postMessage(jsonResult("error", "listening failed: ${aiHandle?.code}"))
                port.disconnect()
            }
            if (audioRecord == null) createAudioRecord()
            audioRecord?.startRecording()
            var status = AiStatus.BEGIN
            val data = ByteArray(BUFFER_SIZE)
            // 20s timeout
            /*val job = lifecycleCoroutineScope.launch {
                delay(20.seconds)
                if (status == AiStatus.CONTINUE) {
                    audioRecord?.stop()
                    thread { end() }
                }
            }*/
            while (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = audioRecord!!.read(data, 0, BUFFER_SIZE)
                if (AudioRecord.SUCCESS < read) {
                    doWrite(data, status)
                    status = AiStatus.CONTINUE
                }
            }
//            if (!job.isCompleted) job.cancel()
        }

    }

    private fun createAudioRecord() {
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            BUFFER_SIZE
        )
    }

    @Synchronized
    private fun doWrite(part: ByteArray, status: AiStatus) {
        if (port == null) {
            return
        }
        val dataBuilder = AiRequest.builder()
        var ret: Int
        Log.d(TAG, "STATUS: " + status.value)

        val aiAudio = AiAudio.get("PCM").data(part).status(status).valid()
        dataBuilder.payload(aiAudio)

        ret = AiHelper.getInst().write(dataBuilder.build(), aiHandle)
        if (ret == 0) {
            ret = AiHelper.getInst().read(ABILITY_ID, aiHandle)
            if (ret != 0) {
                port?.postMessage(jsonResult("error", "failed to read response data: $ret"))
            }
        } else {
            port?.postMessage(jsonResult("error", "failed to write recorded data: $ret"))
        }
        if (status == AiStatus.END) {
            if (aiHandle != null) {
                val ret = AiHelper.getInst().end(aiHandle)
                aiHandle = null
            }
        }
    }

    fun stopListening(): Boolean {
        audioRecord?.stop()
        return end().also {
            port = null
        }
    }

    fun end(): Boolean {
        if (port == null) {
            return false
        }
        doWrite(ByteArray(0), AiStatus.END)
        val ret = AiHelper.getInst().end(aiHandle)
        return ret == 0
    }


    fun jsonResult(type: String, data: Any?): JSONObject {
        return JSONObject().put("type", type).put("data", data)
    }

    fun Collection<Pair<String?, *>>.toJsonObject(): JSONObject {
        return JSONObject().apply {
            forEach { (key, value) ->
                if (key != null) {
                    put(key, value)
                }
            }
        }
    }
}