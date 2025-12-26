# GeckoView App

simply integrated geckoview, a firefox webview component.

## How to use

1. create a [GeckoConfigurer](app/src/main/kotlin/re/rickmoo/gecko/infra/GeckoConfigurer.kt) object, and use
   `addExtensionDependency` to add a component that you want to call through web application. If you not pass a name for
   the dependency, then lower case of the first letter of class name will be used. Which name will be used in later.
2. write your own function to export to the website. the function defines the shape that you want web to call it;
   inside the function, pass a `NativeMessage` object defined at [type.d.js](app/src/main/assets/messaging/type.d.js) to
   invoke the [callNative](app/src/main/assets/messaging/content_script.js) function thus send the message to the
   native.
3. in `NativeMessage` class, there's some parameters that you need attention.
    - type: only two types for now: 1. one time message `CALL_NATIVE_ONE_TIME` 2.stream message
      `CALL_NATIVE_CONNECTION`. it's obviously that one time message is just return a result; stream message is used for
      create a connection to continuously receiving message.
    - extensionName: this is the name of dependencies that you added by `GeckoConfigurer`, native message handler use
      this to identify which dependency would be used.
    - action: after use `extensionName` to find the specific extension, then the name of method as same as action will
      be invoked.
    - data: pass this data to the action. support named parameters or indexed parameters. the first use JSON object
      carrier the arguments, and the last use JSON array. In additionally, if only one argument needed by the action,
      either entire JSON object or the first element of JSON array will be used.

4. define your own native method. method arguments is converted by Jackson ObjectMapper, so all ObjectMapper compatible
   type should be fine. besides, the `org.json.JSONObject` and `org.json.JSONArray` is treated as special, you can
   directly use them are method arguments.

# TODO

- [x] update checking
- [ ] stream message
- [ ] more activity configuration
- [ ] Glide cache

