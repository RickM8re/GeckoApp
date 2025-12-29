/**
 * @param message {NativeMessage}
 * @param sender {MessageSender}
 * @param sendResponse {(any)=>void}
 * @return {boolean}
 * */
const listener = function (message, sender, sendResponse) {
    switch (message.type) {
        case 'CALL_NATIVE_ONE_TIME': {
            let sending = browser.runtime.sendNativeMessage(message.extensionName, message.payload)
            sending.then(sendResponse)
            return true
        }
        case 'CALL_NATIVE_CONNECTION': {
            let port = browser.runtime.connectNative(message.extensionName)
            port.onMessage.addListener((m) => {
                sendResponse(m);
            })
            port.postMessage(message.payload)
            return true
        }
    }
}
browser.runtime.onMessage.addListener(listener);

function enablePreRequestCache() {
    browser.webRequest.onBeforeRequest.addListener(
        function (details) {
            // 过滤掉已经是 content:// 的请求，防止死循环
            if (details.url.startsWith("content://") || details.url.startsWith("blob:")) {
                return {};
            }

            // 将原始 URL 编码，拼接到 content provider 路径后
            const safeUrl = encodeURIComponent(details.url);
            const contentUri = `content://re.rickmoo.gecko.infra.GlideContentProvider?url=${safeUrl}`;

            console.log("Redirecting " + details.url + " to " + contentUri);

            return fetch(contentUri)
                .then(response => response.blob())
                .then(blob => {
                    // 4. 创建一个临时的 Blob URL
                    const blobUrl = URL.createObjectURL(blob);
                    console.log("Redirecting to Blob: " + blobUrl);
                    return {redirectUrl: blobUrl};
                })
                .catch(error => {
                    console.error("Fetch failed", error);
                    // 如果失败，不拦截，让它走原始网络请求（或返回一个错误图）
                    return {};
                });
        },
        {urls: ["<all_urls>"], types: ["image"]}, // 只拦截图片
        ["blocking"] // 必须是阻塞模式才能重定向
    );
}