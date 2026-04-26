# GPX Trail Runbook

## Use This Checkout

- Active local checkout: `C:\Users\Admin\GitHub\gpx-trail`
- GitHub remote: `https://github.com/ajbrownoh/gpx-trail.git`
- Legacy copy: `C:\Users\Admin\Downloads\wear-tracker\wear-tracker`

Use the `GitHub\gpx-trail` checkout for all new edits.

## Project Modules

- Watch app: `:app`
- Phone companion: `:mobile`
- Watch package name: `com.dirtbike.weartracker`
- App name: `GPX Trail`

## Important PowerShell Note

If commands work in one PowerShell window but fail in another, it is usually because the new window does not have the Android/Java environment variables set.

Run the setup block below again in every new terminal window before building.

## One-Time Setup Per Terminal

```powershell
Set-Location C:\Users\Admin\GitHub\gpx-trail
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='C:\Users\Admin\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT='C:\Users\Admin\AppData\Local\Android\Sdk'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$adb='C:\Users\Admin\AppData\Local\Android\Sdk\platform-tools\adb.exe'
```

## Build

Build both modules:

```powershell
.\gradlew.bat assembleDebug
```

Watch APK:

```text
app\build\outputs\apk\debug\app-debug.apk
```

Phone APK:

```text
mobile\build\outputs\apk\debug\mobile-debug.apk
```

## Push Changes To The Watch

### 1. Turn On Wireless Debugging On The Watch

On the watch:

1. Open `Settings`
2. Open `Developer options`
3. Turn on `ADB debugging`
4. Turn on `Wireless debugging`
5. Copy the current IP and port shown there

Example:

```text
192.168.1.97:41517
```

### 2. Build, Install, Relaunch

Replace the IP:port with the current value shown on the watch:

```powershell
Set-Location C:\Users\Admin\GitHub\gpx-trail
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='C:\Users\Admin\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT='C:\Users\Admin\AppData\Local\Android\Sdk'
$adb='C:\Users\Admin\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$watch='192.168.1.97:41517'
$watchApk='C:\Users\Admin\GitHub\gpx-trail\app\build\outputs\apk\debug\app-debug.apk'

.\gradlew.bat assembleDebug
& $adb connect $watch
& $adb -s $watch install -r $watchApk
& $adb -s $watch shell am force-stop com.dirtbike.weartracker
& $adb -s $watch shell am start -n com.dirtbike.weartracker/.MainActivity
& $adb -s $watch shell pidof com.dirtbike.weartracker
```

If the last command prints a PID, the app is running.

## Quick Verify Commands

List connected devices:

```powershell
& $adb devices -l
```

Check whether the app is running:

```powershell
& $adb -s $watch shell pidof com.dirtbike.weartracker
```

Check recent crash logs:

```powershell
& $adb -s $watch logcat -d -t 250 | Select-String -Pattern 'AndroidRuntime|FATAL EXCEPTION|com.dirtbike.weartracker'
```

## If The Watch Port Changes

The wireless debugging port changes often.

If `adb connect` fails:

1. Re-open `Wireless debugging` on the watch
2. Get the new IP:port
3. Re-run the install block with the new value

## If ADB Shows The Watch By Name Instead Of IP

Sometimes `adb devices` shows the watch with an mDNS entry like:

```text
adb-RFAY20X801Y-SxVVwq._adb-tls-connect._tcp
```

Use that exact device id:

```powershell
$watch='adb-RFAY20X801Y-SxVVwq._adb-tls-connect._tcp'
& $adb -s $watch install -r $watchApk
& $adb -s $watch shell am force-stop com.dirtbike.weartracker
& $adb -s $watch shell am start -n com.dirtbike.weartracker/.MainActivity
```

## If The Watch Needs To Pair Again

On the watch:

1. Open `Developer options > Wireless debugging`
2. Choose `Pair new device`
3. Use the pairing IP:port and pairing code shown there

Then run:

```powershell
& $adb pair 192.168.1.97:PAIRING_PORT
```

After pairing, connect using the normal wireless debugging port, not the pairing port.

## Git Workflow

Check status:

```powershell
git status -sb
```

Commit:

```powershell
git add .
git commit -m "Describe the change"
```

Push:

```powershell
git push
```

## Useful Paths

- Watch import logic:
  `app\src\main\kotlin\com\dirtbike\weartracker\data\ImportedGpxRepository.kt`
- Watch import receiver:
  `app\src\main\kotlin\com\dirtbike\weartracker\transfer\WatchGpxImportService.kt`
- Watch phone export sender:
  `app\src\main\kotlin\com\dirtbike\weartracker\transfer\PhoneTransferClient.kt`
- Saved session confirmation screen:
  `app\src\main\kotlin\com\dirtbike\weartracker\ui\SaveConfirmScreen.kt`

## Current UX Notes

- Stopping a live session requires a second tap within 2.5 seconds.
- Discarding a completed session also requires a second tap within 2.5 seconds.
- Imported GPX files are listed in Saved Sessions and also feed the waypoint system.
- Exact duplicate GPX imports are ignored/reused.
- Same-name but different GPX imports are stored as unique files like `_2`, `_3`, etc.

