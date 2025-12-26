package re.rickmoo.gecko.component

import org.json.JSONObject
import org.mozilla.gecko.util.GeckoBundle

class GeckoBridge {
    fun communicationTest(data: JSONObject): Any {
        return GeckoBundle().apply {
            data.keys().forEach { key ->
                putString(key, data[key].toString())
            }
        }
    }
}