#!/bin/bash
set -e

export ANDROID_HOME=/home/codespace/android-sdk
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

cd /home/codespace

# Set up Android SDK if not already done
if [ ! -d "$ANDROID_HOME/platforms/android-36" ]; then
    echo "=== Setting up Android SDK ==="
    
    # Download cmdline tools if needed
    if [ ! -f cmdline-tools.zip ]; then
        wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline-tools.zip
    fi
    
    # Extract
    mkdir -p $ANDROID_HOME/cmdline-tools
    unzip -q -o cmdline-tools.zip -d $ANDROID_HOME/cmdline-tools/
    mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest 2>/dev/null || true
    
    # Accept licenses
    mkdir -p $ANDROID_HOME/licenses
    echo -e "\n24333f8a63b6825ea9c5514f83c2829b004d1fee" > $ANDROID_HOME/licenses/android-sdk-license
    echo -e "\n84831b9409646a918e30573bab4c9c91346d8abd" >> $ANDROID_HOME/licenses/android-sdk-license
    echo -e "\nd975f751698a77b662f1254ddbeed3901e976f5a" >> $ANDROID_HOME/licenses/android-sdk-license
    
    # Install SDK components
    $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools"
    echo "=== SDK installed ==="
else
    echo "=== SDK already installed ==="
fi

# Set up project
cd /home/codespace/keymapper
echo "sdk.dir=$ANDROID_HOME" > local.properties

# Build
chmod +x gradlew
./gradlew assembleDebug --no-daemon 2>&1 | tail -30

echo ""
echo "=== BUILD RESULT ==="
if [ -f app/build/outputs/apk/debug/app-debug.apk ]; then
    echo "SUCCESS! APK built:"
    ls -lh app/build/outputs/apk/debug/app-debug.apk
else
    echo "FAILED - no APK found"
    ls -lh app/build/outputs/apk/debug/ 2>/dev/null || echo "No debug output dir"
fi
