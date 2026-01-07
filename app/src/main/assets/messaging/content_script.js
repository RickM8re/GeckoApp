/**
 *
 *
 * @param message {NativeMessage}
 * @return {Promise<any>}
 * */
function callNative(message) {
    return new window.Promise((resolve, reject) => {
        // 执行扩展内部的异步操作
        browser.runtime.sendMessage(message).then(response => {
            // 【重要】结果数据也必须克隆到网页作用域
            // 如果 response 是对象，不 cloneInto 依然会报错或无法读取
            try {
                if (response instanceof Error) {
                    reject(new window.Error(response.message || "Native Bridge Error"));
                } else
                    resolve(cloneInto(response, window, {cloneFunctions: true}));
            } catch (e) {
                // 如果是简单类型(string/int)，直接返回即可
                resolve(response);
            }
        }).catch(error => {
            // 把错误信息也克隆过去（可选，视错误对象复杂度而定）
            reject(new window.Error(error.message || "Native Bridge Error"));
        });
    });
}

/**
 * @param message {StreamNativeMessage}
 */
function callNativeStream(message) {
    const name = message.extensionName
    const backgroundPort = browser.runtime.connect({name});
    backgroundPort.onMessage.addListener((response) => {
        message.consumer?.(response);
    })
    backgroundPort.onDisconnect.addListener((port) => {
        message.onDisconnect?.(port)
    })
    return backgroundPort
}

const bridgeObject = {
    geckoBridge: {
        communicationTest: function (data) {
            return callNative(new OneTimeNativeMessage('geckoBridge', 'communicationTest', data))
        }
    },
    activityConfiguration: {
        setSystemUiVisible: function (visibility) {
            return callNative(new OneTimeNativeMessage('activityConfiguration', 'setSystemUiVisible', visibility))
        },
        edgeToEdge: function (statusBarStyle, navigationBarStyle) {
            return callNative(new OneTimeNativeMessage('activityConfiguration', 'edgeToEdge',
                {statusBarStyle, navigationBarStyle}));
        }
    },
    iflytek: {
        init(appId, apiKey, apiSecret) {
            return callNative(new OneTimeNativeMessage('iflytek', 'init', [appId, apiKey, apiSecret]));
        },
        /**
         * @param onListening {(any)=>void}
         * @param onDisconnect {function(Port):void}
         * @param sender {()=>any}
         * */
        startListening: (onListening, onDisconnect, sender) => {
            callNativeStream(new StreamNativeMessage('iflytek', onListening, onDisconnect, sender))
        },
        stopListening() {
            return callNative(new OneTimeNativeMessage('iflytek', 'stopListening'))
        },
        loadCustomText() {
            return callNative(new OneTimeNativeMessage('iflytek', 'loadCustomText'))
        },
        unloadCustomText() {
            return callNative(new OneTimeNativeMessage('iflytek', 'unloadCustomText'))
        }
    }
}

try {

    /**
     * exportFunction @type {(func: Function, targetScope: Object, options?: {defineAs?: string, allowCallbacks?: boolean}) => void}
     * cloneInto @type {<T>(obj: T, targetScope: Object, options?: {cloneFunctions?: boolean}) => T}
     * */
    window.wrappedJSObject.nativeBridge = cloneInto(bridgeObject, window, {cloneFunctions: true});
    console.log("Android Bridge injected successfully");
} catch (e) {
    // 降级处理（非严格安全模式下）
    window.wrappedJSObject.androidBridge = callNative;
    console.error("Android Bridge inject failed");

}