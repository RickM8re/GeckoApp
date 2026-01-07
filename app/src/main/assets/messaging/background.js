/**
 * @param message {NativeMessage}
 * @param sender {MessageSender}
 * @param sendResponse {(any)=>void}
 * @return {boolean}
 * */
const listener = function (message, sender, sendResponse) {
    let sending = browser.runtime.sendNativeMessage(message.extensionName, message.payload)
    sending.then(sendResponse).catch(sendResponse)
    return true
}
browser.runtime.onMessage.addListener(listener);

/**
 * @param contentPort {Port}
 * */
const streamListener = function (contentPort) {
    const extensionName = contentPort.name
    const nativePort = browser.runtime.connectNative(extensionName);
    const nativePortDisconnect = () => {
        contentPort.onDisconnect.removeListener(contentPortDisconnect)
        contentPort.disconnect()
    }
    const contentPortDisconnect = () => {
        nativePort.onDisconnect.removeListener(nativePortDisconnect)
        nativePort.disconnect()
    }
    nativePort.onDisconnect.addListener(nativePortDisconnect);
    nativePort.onMessage.addListener((response) => {
        contentPort.postMessage(response)
    })
    contentPort.onMessage.addListener((response) => {
        nativePort.postMessage(response)
    })
}

browser.runtime.onConnect.addListener(streamListener)

function enablePreRequestCache() {
    browser.webRequest.onHeadersReceived.addListener(
        function (details) {
            const cleanHeaders = details.responseHeaders.filter(header => {
                const name = header.name.toLowerCase();
                return !['pragma',].includes(name) || !(name === 'cache-control' && header.value.toLowerCase() === 'no-store') ||
                    !('expires' === name && header.value.toLowerCase() === '0');
            });

            if (!cleanHeaders.some((header) => {
                return header.name.toLowerCase() === 'cache-control'
            }))
                cleanHeaders.push({
                    name: "Cache-Control",
                    value: "public, max-age=1209600, immutable" //2 weeks
                });

            return {responseHeaders: cleanHeaders};
        },
        {urls: ["<all_urls>"], types: ["image"]},
        ["blocking", "responseHeaders"]
    );
}