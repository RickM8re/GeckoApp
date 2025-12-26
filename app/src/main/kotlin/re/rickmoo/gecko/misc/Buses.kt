package re.rickmoo.gecko.misc

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import re.rickmoo.gecko.service.update.UpdateInfoBusCarrier

object UpdateBus {
    private val _updateFlow = MutableSharedFlow<UpdateInfoBusCarrier>()
    val updateFlow = _updateFlow.asSharedFlow()

    suspend fun emitUpdate(info: UpdateInfoBusCarrier) {
        _updateFlow.emit(info)
    }

}