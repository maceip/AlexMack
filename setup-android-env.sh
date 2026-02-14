#!/usr/bin/env bash
#
# Android SDK + Emulator setup for headless CI / agent environments (Jules, Codespaces, etc.)
# Creates a fully working Android build environment with an API 36 AVD.
#
# Usage:
#   chmod +x setup-android-env.sh
#   ./setup-android-env.sh          # install SDK + create AVD
#   ./setup-android-env.sh --start  # also boot the emulator
#
set -euo pipefail

ANDROID_API=36
BUILD_TOOLS="36.0.0"
SYSTEM_IMAGE="system-images;android-${ANDROID_API};google_apis;x86_64"
AVD_NAME="api${ANDROID_API}"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

info()  { echo -e "\033[1;34m[setup]\033[0m $*"; }
ok()    { echo -e "\033[1;32m[  ok ]\033[0m $*"; }
fail()  { echo -e "\033[1;31m[fail]\033[0m $*" >&2; exit 1; }

# ---------------------------------------------------------------------------
# 1. System dependencies
# ---------------------------------------------------------------------------
info "Installing system dependencies..."
if command -v apt-get &>/dev/null; then
    sudo apt-get update -qq
    sudo apt-get install -y -qq \
        openjdk-21-jdk-headless \
        unzip \
        wget \
        libpulse0 \
        libnss3 \
        libglu1-mesa \
        cpu-checker \
        qemu-kvm 2>/dev/null || true
elif command -v dnf &>/dev/null; then
    sudo dnf install -y -q \
        java-21-openjdk-headless \
        unzip \
        wget \
        qemu-kvm 2>/dev/null || true
fi

# Check Java
java -version 2>/dev/null || fail "Java not found. Install JDK 17+ manually."
ok "Java available"

# Check KVM (optional but makes emulator usable)
if [ -w /dev/kvm ] 2>/dev/null; then
    ok "KVM acceleration available"
else
    echo "    Warning: /dev/kvm not writable — emulator will be SLOW (software rendering)"
    echo "    For GCE: ensure nested virtualization is enabled on the VM"
fi

# ---------------------------------------------------------------------------
# 2. Android command-line tools
# ---------------------------------------------------------------------------
if [ -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
    ok "Android cmdline-tools already installed"
else
    info "Downloading Android command-line tools..."
    mkdir -p "$ANDROID_HOME/cmdline-tools"
    TMPZIP=$(mktemp /tmp/cmdline-tools-XXXXXX.zip)
    wget -q -O "$TMPZIP" "$CMDLINE_TOOLS_URL"
    unzip -q -o "$TMPZIP" -d "$ANDROID_HOME/cmdline-tools"
    mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest" 2>/dev/null || true
    rm -f "$TMPZIP"
    ok "Command-line tools installed"
fi

# ---------------------------------------------------------------------------
# 3. Accept licenses + install SDK components
# ---------------------------------------------------------------------------
info "Accepting licenses..."
yes | sdkmanager --licenses >/dev/null 2>&1 || true

info "Installing SDK components (API ${ANDROID_API})..."
sdkmanager --install \
    "platform-tools" \
    "platforms;android-${ANDROID_API}" \
    "build-tools;${BUILD_TOOLS}" \
    "emulator" \
    "${SYSTEM_IMAGE}" \
    2>&1 | grep -E '^\[|done|Warning' || true

ok "SDK components installed"

# ---------------------------------------------------------------------------
# 4. Create AVD
# ---------------------------------------------------------------------------
if avdmanager list avd 2>/dev/null | grep -q "Name: ${AVD_NAME}"; then
    ok "AVD '${AVD_NAME}' already exists"
else
    info "Creating AVD '${AVD_NAME}'..."
    echo "no" | avdmanager create avd \
        --name "${AVD_NAME}" \
        --package "${SYSTEM_IMAGE}" \
        --device "pixel_6" \
        --force
    ok "AVD '${AVD_NAME}' created"
fi

# ---------------------------------------------------------------------------
# 5. Write shell profile exports
# ---------------------------------------------------------------------------
PROFILE_LINE="export ANDROID_HOME=\"$ANDROID_HOME\" ANDROID_SDK_ROOT=\"$ANDROID_HOME\" PATH=\"$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:\$PATH\""

for rc in "$HOME/.bashrc" "$HOME/.zshrc"; do
    if [ -f "$rc" ] && ! grep -q 'ANDROID_HOME' "$rc" 2>/dev/null; then
        echo "" >> "$rc"
        echo "# Android SDK (added by setup-android-env.sh)" >> "$rc"
        echo "$PROFILE_LINE" >> "$rc"
        ok "Added ANDROID_HOME to $rc"
    fi
done

# ---------------------------------------------------------------------------
# 6. Verify Gradle can see the SDK
# ---------------------------------------------------------------------------
if [ -f "local.properties" ] && grep -q 'sdk.dir' local.properties; then
    ok "local.properties already has sdk.dir"
else
    echo "sdk.dir=$ANDROID_HOME" > local.properties
    ok "Wrote local.properties with sdk.dir=$ANDROID_HOME"
fi

# ---------------------------------------------------------------------------
# 7. Optionally start the emulator
# ---------------------------------------------------------------------------
if [[ "${1:-}" == "--start" ]]; then
    info "Starting emulator (headless, no window)..."
    nohup emulator -avd "${AVD_NAME}" \
        -no-window \
        -no-audio \
        -no-boot-anim \
        -gpu swiftshader_indirect \
        -memory 4096 \
        -partition-size 4096 \
        > /tmp/emulator.log 2>&1 &

    info "Waiting for emulator to boot (timeout 180s)..."
    adb wait-for-device
    SECONDS=0
    while [ $SECONDS -lt 180 ]; do
        BOOT=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
        if [ "$BOOT" = "1" ]; then
            ok "Emulator booted in ${SECONDS}s"
            adb shell settings put global window_animation_scale 0
            adb shell settings put global transition_animation_scale 0
            adb shell settings put global animator_duration_scale 0
            ok "Animations disabled for testing"
            break
        fi
        sleep 2
    done

    if [ "$BOOT" != "1" ]; then
        fail "Emulator did not boot within 180s. Check /tmp/emulator.log"
    fi
fi

# ---------------------------------------------------------------------------
# Done
# ---------------------------------------------------------------------------
echo ""
ok "Android environment ready!"
echo "    ANDROID_HOME=$ANDROID_HOME"
echo "    API level:   $ANDROID_API"
echo "    AVD name:    $AVD_NAME"
echo ""
echo "    Build:       ./gradlew assembleDebug"
echo "    Test:        ./gradlew testDebugUnitTest"
echo "    Emulator:    ./setup-android-env.sh --start"
echo "    Instrumented: ./gradlew connectedDebugAndroidTest"
