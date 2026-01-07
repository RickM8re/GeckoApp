package re.rickmoo.gecko.infra

import org.mozilla.geckoview.WebExtension

interface Portable {
    fun withPort(port: WebExtension.Port)
}