package re.rickmoo.gecko.service.update

import java.time.LocalDateTime

data class UpdateIndexModel(
    val version: String,
    val versionName: String,
    val versionCode: Int,
    val date: LocalDateTime,
    val changeLog: String?,
    val type: String,
    val apks: HashMap<String, String>?
)

data class UpdateInfoBusCarrier(
    val downloadUrl: String,
    val changeLogUrl: String?,
    val updateInfo: UpdateIndexModel,
)
