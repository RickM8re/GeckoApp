class NativeMessage {
    /**
     * @type {string}
     * */
    extensionName;
    /**
     * @type {{action:string,data:undefined|any}}
     * */
    payload;

    /**
     * @return {string}
     * */
    type;

    /**
     * @param extensionName {string}
     * @param action {string|null}
     * */
    constructor(extensionName, action = null) {
        if (new.target === NativeMessage) {
            throw new Error("Use subclass instead");
        }
        this.extensionName = extensionName;
        this.payload = {action};
    }
}

/**
 * @extends NativeMessage
 * */
class OneTimeNativeMessage extends NativeMessage {
    /**
     * @param extensionName {string}
     * @param action {string}
     * @param data {object|array|null}
     * */
    constructor(extensionName, action, data = null) {
        super(extensionName, action);
        this.payload.data = data;
    }
}


/**
 * @extends NativeMessage
 * */
class StreamNativeMessage extends NativeMessage {

    /**
     * @callback onDisconnect
     * @param port {Port}
     * @returns {void}
     */
    onDisconnect;
    /**
     * @callback StreamOutputHandler
     * @param data {any} - 从流中接收到的数据
     * @returns {void}
     */
    consumer;
    /**
     * @callback StreamInputHandler
     * @param data {any}  - 要写入流的数据块
     * @returns {void}
     */
    supplier;

    /**
     * @param extensionName {string}
     * @param onDisconnect {function(Port): void}
     * @param supplier {StreamInputHandler|undefined}
     * @param consumer {StreamOutputHandler|undefined}
     * */
    constructor(extensionName, consumer, onDisconnect, supplier) {
        super(extensionName);
        this.onDisconnect = onDisconnect;
        /** @type {StreamOutputHandler|undefined} */
        this.consumer = consumer;
        /** @type {StreamInputHandler|undefined} */
        this.supplier = supplier;
    }

    get type() {
        return 'CALL_NATIVE_CONNECTION';
    }
}