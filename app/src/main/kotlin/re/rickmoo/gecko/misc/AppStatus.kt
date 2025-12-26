package re.rickmoo.gecko.misc

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner

object AppStatus {
    fun isAppForeground(): Boolean {
        return ProcessLifecycleOwner.get().lifecycle.currentState
            .isAtLeast(Lifecycle.State.STARTED)
    }
}