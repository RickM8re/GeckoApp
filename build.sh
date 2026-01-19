#!/bin/bash
# use home for flow
export ANDROID_HOME=$HOME/android-sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$PATH
mkdir -p $ANDROID_HOME/cmdline-tools

rm -rf $ANDROID_HOME/cmdline-tools/latest

if [ ! -d "$ANDROID_HOME/cmdline-tools" ]; then
    curl -o tools.zip https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
    unzip -q tools.zip -d $ANDROID_HOME/cmdline-tools
    mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest
fi

(yes || true) sdkmanager --licenses > /dev/null ||


sdkmanager "platform-tools" "platforms;android-36.1" "build-tools;36.0.0"

chmod +x ./gradlew

# build debug package for now
./gradlew assembleDebug