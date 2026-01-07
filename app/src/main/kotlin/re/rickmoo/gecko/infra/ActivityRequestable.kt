package re.rickmoo.gecko.infra

import android.content.Intent

interface ActivityRequestable {
    fun requestPermission(permission: String)
    fun requestPermission(permission: Array<String>)
    fun requestContent(mimeTypes: Array<String>)
    fun requestMultipleContent(mimeTypes: Array<String>)
    fun requestActivityResult(intent: Intent)
}