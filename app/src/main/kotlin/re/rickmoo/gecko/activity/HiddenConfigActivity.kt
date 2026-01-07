package re.rickmoo.gecko.activity


import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import re.rickmoo.gecko.BuildConfig
import re.rickmoo.gecko.compose.HiddenScreen
import re.rickmoo.gecko.datasource.Preferences
import re.rickmoo.gecko.misc.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url

class HiddenConfigActivity : ComponentActivity() {

    private val configUrl = "${BuildConfig.UPDATE_BASE_URL.trimEnd('/')}/envs.json"
    private val preferences by lazy { Preferences(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HiddenScreen(configUrl, preferences)
                }
            }
        }
    }
}


class HiddenConfigModel(private val url: String, private val preferences: Preferences) : ViewModel() {
    private val geckoUrlApi by lazy { Retrofit.create(GeckoUrlApi::class) }
    private val _userList = MutableStateFlow<List<GeckoUrl>>(emptyList())
    val userList = _userList.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _currentEnv = MutableStateFlow<GeckoUrl?>(null)
    val currentEnv = _currentEnv.asStateFlow()
    private val _updateChannel = MutableStateFlow("")
    val updateChannel = _updateChannel.asStateFlow()

    fun setChannel(channel: String) {
        _updateChannel.value = channel
    }

    fun setEnv(env: GeckoUrl) {
        _currentEnv.value = env
        viewModelScope.launch {
            preferences.remove(Preferences.GeckoView.RESTORE_URL)
        }
    }

    init {
        viewModelScope.launch {
            fetchData()
        }
    }

    private suspend fun fetchData() {
        try {
            _userList.value = geckoUrlApi.getGeckoUrlList(url)
            val envId = preferences[Preferences.GeckoView.ENV_ID]
            _currentEnv.value = _userList.value.firstOrNull { it.id == envId }
            _updateChannel.value = preferences[Preferences.App.UPDATE_CHANNEL] ?: ""
        } catch (e: Exception) {
            Log.e("HiddenConfigModel", "Error to fetch gecko urls", e)
            _userList.value = listOf(
                GeckoUrl(
                    "",
                    "失败",
                    "获取配置失败, 请联系我们",
                    "https://www.bilibili.com"
                )
            )
        } finally {
            _isLoading.value = false
        }
    }
}


interface GeckoUrlApi {
    @GET
    suspend fun getGeckoUrlList(@Url url: String): List<GeckoUrl>
}

data class GeckoUrl(val id: String, val name: String, val desc: String, val url: String)