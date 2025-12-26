package re.rickmoo.gecko.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import org.mozilla.geckoview.GeckoView
import re.rickmoo.gecko.misc.UpdateBus
import re.rickmoo.gecko.service.update.UpdateInfoBusCarrier


@Composable
fun GeckoView(geckoView: GeckoView, lifecycle: Lifecycle) {
    var showDialog by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfoBusCarrier?>(null) }
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            UpdateBus.updateFlow.collect { info ->
                updateInfo = info
                showDialog = true
            }
        }
    }

    // 只要 showDialog 为 true 且 info 不为空，就显示 Dialog
    if (showDialog && updateInfo != null) {
        UpdateDialog(
            versionInfo = updateInfo!!,
            onDismiss = { showDialog = false },
        )
    }

    AndroidView(
        { geckoView },
        Modifier.fillMaxSize(),
    )
}