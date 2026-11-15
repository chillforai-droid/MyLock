# MY LOCK — Build & Release APK Instructions

## 1. GitHub Actions Automated APK Build (Recommended)
A ready-to-run workflow is included at `.github/workflows/android-apk.yml`.
1. Push this repository to GitHub (`main` or `master` branch) — or use **Push to GitHub** in AI Studio.
2. Open your GitHub repository → **Actions** tab → **Build My Lock Android APK**.
3. The workflow automatically:
   - Installs JDK 21 and Gradle 9.3.1
   - Decodes `debug.keystore` from `debug.keystore.base64`
   - Runs `gradle :app:testDebugUnitTest`
   - Builds `app/build/outputs/apk/debug/app-debug.apk`
   - Uploads `my-lock-debug-apk` under **Artifacts** for download.

### Optional Signed Release APK on GitHub
Add these Repository Secrets in GitHub (**Settings → Secrets and variables → Actions**):
- `UPLOAD_KEYSTORE_BASE64`: Base64 string of your `.jks` keystore (`base64 -w 0 my-upload-key.jks`)
- `STORE_PASSWORD`: Keystore password
- `KEY_PASSWORD`: Key alias (`upload`) password

When all three secrets exist, GitHub Actions also builds and uploads `my-lock-release-apk`.

---

## 2. Local Android Studio / CLI Build
### Prerequisites
- Android Studio Ladybug / Meerkat or newer (AGP 9.1.1, Kotlin 2.2.10, JDK 21)
- Android SDK Platform 36 (`compileSdk = 36`, `targetSdk = 36`, `minSdk = 24`)

### Debug APK Build
```bash
base64 -d debug.keystore.base64 > debug.keystore   # if debug.keystore is not present
gradle assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

### Run Unit & Multi-API Robolectric Tests
```bash
gradle :app:testDebugUnitTest
```

### Release APK Build
```bash
export KEYSTORE_PATH=/path/to/my-upload-key.jks
export STORE_PASSWORD=your_store_password
export KEY_PASSWORD=your_key_password
gradle assembleRelease
```
Output: `app/build/outputs/apk/release/app-release.apk`
