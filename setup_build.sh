#!/bin/bash
set -e

export ANDROID_HOME=/workspaces/android-sdk
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

cd /workspaces

# Extract cmdline tools
unzip -q -o cmdline-tools.zip -d android-sdk/cmdline-tools/
mv android-sdk/cmdline-tools/cmdline-tools android-sdk/cmdline-tools/latest 2>/dev/null || true

# Accept licenses and install SDK
yes | android-sdk/cmdline-tools/latest/bin/sdkmanager --licenses 2>&1 | tail -2
android-sdk/cmdline-tools/latest/bin/sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools" 2>&1 | tail -5

echo "=== SDK installed ==="

# Set up project
cd /workspaces/keymapper
echo "sdk.dir=/workspaces/android-sdk" > local.properties

# Build
chmod +x gradlew
./gradlew assembleDebug --no-daemon 2>&1 | tail -20

echo "=== BUILD DONE ==="
ls -lh app/build/outputs/apk/debug/ 2>/dev/null
