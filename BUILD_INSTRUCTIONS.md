# Compilation and Installation Instructions

This guide outlines how to compile the WaEnhancer debug APK from the source code and how to install it via ADB on a rooted Android device.

## 1. Compiling the APK

To compile a new debug APK, you must ensure that Java 17 is available in your PATH. The project uses the Gradle wrapper to build the Android application.

Run the following command from the root of the project repository:

```bash
# Set Java 17 Home and add it to PATH
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"

# Set a local Gradle user home if needed, and run the assemble task
GRADLE_USER_HOME="$(pwd)/.gradle" ./gradlew :app:assembleWhatsappDebug
```

If the compilation is successful, the newly built APK will be generated at:
`app/build/outputs/apk/whatsapp/debug/app-whatsapp-debug.apk`

## 2. Installing via ADB

Because the local debug build uses a different signing key than the public released version of WaEnhancer, installing over an existing public release will cause a `INSTALL_FAILED_UPDATE_INCOMPATIBLE` signature conflict. You must cleanly uninstall the existing app first.

### Step 1: Authorize Device
Ensure your Android device is connected via USB and USB Debugging is enabled. Run `adb devices` and ensure your device shows as `device` (not `unauthorized`).

### Step 2: Uninstall Existing Application
Completely remove the existing WaEnhancer application to avoid signature conflicts. Note that this will erase existing WaEnhancer preferences:

```bash
adb uninstall com.wmods.wppenhacer
```

### Step 3: Install the New Debug APK
Install the freshly compiled APK. The `-r` flag replaces existing app data (if applicable), and `-d` allows version downgrades.

```bash
adb install -r -d app/build/outputs/apk/whatsapp/debug/app-whatsapp-debug.apk
```

### Step 4: Restart Target Applications
To ensure the new Xposed hooks are loaded correctly, force-stop WhatsApp and launch the WaEnhancer settings menu to configure your modules.

```bash
# Force stop WhatsApp to reload the hooks
adb shell am force-stop com.whatsapp

# Launch the WaEnhancer main activity
adb shell monkey -p com.wmods.wppenhacer -c android.intent.category.LAUNCHER 1
```

> **Note:** After a clean install, remember to re-enable WaEnhancer inside your LSPosed manager and reboot your device (or perform a soft reboot) to finalize the module injection.
