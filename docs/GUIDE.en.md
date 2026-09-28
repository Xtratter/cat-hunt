---
title: Building Android apps on a phone
tags:
  - android
  - gradle
  - kotlin
  - guide
created: 2026-09-27
updated: 2026-09-28
---

# Building Android apps on a phone

[Русский](GUIDE.md) · **English**

> [!NOTE]
> **About this environment.**
> Ubuntu 24.04 runs in a chroot on a phone (POCO F3, ARM64).
> Everything needed for building is already installed; internet is only needed to download dependencies.
>
> This file lives in the [cat-hunt](https://github.com/Xtratter/cat-hunt) repository as `docs/GUIDE.en.md`.
> The Russian original is `docs/GUIDE.md`, and its Obsidian copy is in Downloads: `Сборка Android-приложений.md`.

## Where things are

| What | Where |
|---|---|
| JDK 17 | `/usr/lib/jvm/java-17-openjdk-arm64` |
| Android SDK | `/opt/android-sdk` |
| Build-tools (ARM64) | `/opt/android-sdk/build-tools/35.0.0` |
| Gradle 8.10.2 | `/opt/gradle-8.10.2`, command `gradle` |
| Environment variables | `/etc/profile.d/android.sh` |
| Global Gradle settings | `~/.gradle/gradle.properties` |
| Sample project | `/root/HelloAndroid` |
| Cat Hunt game | `/root/CatGame` |
| DroidTop process monitor | `/root/DroidTop` → [github.com/Xtratter/droidtop](https://github.com/Xtratter/droidtop) |
| APKs of all versions (game and DroidTop) | `/root/releases` |
| APKs ready to install | `/sdcard/Download` |

> [!WARNING]
> **The main quirk.**
> Google's official `aapt2`, `zipalign` and `adb` are built for x86_64 only and **do not run** on the phone.
> They have been replaced with ARM64 builds, and `~/.gradle/gradle.properties` contains:
> ```properties
> android.aapt2FromMavenOverride=/opt/android-sdk/build-tools/35.0.0/aapt2
> ```
> Do not remove this line: without it every build fails.

---

## 1. Quick start: rebuild the sample

```sh
cd /root/HelloAndroid
./gradlew assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk /sdcard/Download/HelloAndroid.apk
```

Then open the file in Downloads and install it.

> [!TIP]
> **The first build is slow.**
> The first run downloads dependencies and takes about a minute. Later builds are much faster.

---

## 2. A new app from the sample

The easiest way is to copy the sample and rename it.

```sh
cp -r /root/HelloAndroid /root/MyApp
cd /root/MyApp
rm -rf .gradle build app/build
```

Then change the package name (for example, to `com.me.myapp`):

1. In `settings.gradle.kts`: `rootProject.name = "MyApp"`
2. In `app/build.gradle.kts`: set `namespace` and `applicationId` to `com.me.myapp`
3. Move the code to the new package folder:
   ```sh
   mkdir -p app/src/main/java/com/me/myapp
   mv app/src/main/java/com/example/hello/* app/src/main/java/com/me/myapp/
   rm -r app/src/main/java/com/example
   sed -i 's/^package com.example.hello/package com.me.myapp/' app/src/main/java/com/me/myapp/*.kt
   ```
4. The app name shown on screen is in `app/src/main/res/values/strings.xml`

> [!NOTE]
> **Different `applicationId` = different apps.**
> If you keep `com.example.hello`, the new app will install **over** the old one.

---

## 3. Project structure

```
MyApp/
├── settings.gradle.kts        ← project name, repositories
├── build.gradle.kts           ← plugin versions (AGP, Kotlin)
├── gradlew                    ← runs the build
├── local.properties           ← sdk.dir=/opt/android-sdk
└── app/
    ├── build.gradle.kts       ← app settings, dependencies
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/…/MainActivity.kt   ← code
        └── res/                     ← strings, images, layouts
```

### Adding a library

In `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
```

> [!WARNING]
> **AndroidX.**
> For AndroidX libraries, add this to the project's `gradle.properties`:
> ```properties
> android.useAndroidX=true
> ```

---

## 4. Building someone else's project (from git)

```sh
cd /root
git clone https://github.com/author/project.git
cd project
echo "sdk.dir=/opt/android-sdk" > local.properties
./gradlew assembleDebug
```

If the build fails, see section 7 "When something goes wrong".

> [!CAUTION]
> **Compatibility.**
> - If the project sets a different `buildToolsVersion`, change it to `"35.0.0"` or remove the line.
> - Projects with **native code** (C/C++, NDK, `externalNativeBuild`) won't build yet: they need an ARM64 NDK.
> - Need another platform (for example, `compileSdk = 34`)? Install it:
>   ```sh
>   sdkmanager "platforms;android-34"
>   ```

---

## 5. Release build (for distribution)

A debug APK is signed with a test key. Publishing in app stores needs your own key.

### Create a key (once)

```sh
keytool -genkeypair -v -keystore /root/my-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias mykey
```

> [!CAUTION]
> **Keep the key safe.**
> Back up `my-release.jks` and remember the password.
> If you lose the key, you can't release updates to the app.

### Use the key in `app/build.gradle.kts`

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("/root/my-release.jks")
            storePassword = "PASSWORD"
            keyAlias = "mykey"
            keyPassword = "PASSWORD"
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }
}
```

> [!CAUTION]
> **No passwords in a public repository.**
> If the project is on GitHub, don't write passwords directly in `build.gradle.kts`.
> Put them in `~/.gradle/gradle.properties` instead (it doesn't go into git).

### Build

```sh
./gradlew assembleRelease
cp app/build/outputs/apk/release/app-release.apk /sdcard/Download/MyApp.apk
```

---

## 6. Useful commands

| Command | What it does |
|---|---|
| `./gradlew assembleDebug` | Build a debug APK |
| `./gradlew assembleRelease` | Build a release APK |
| `./gradlew lintDebug` | Check code and layouts for errors |
| `./gradlew clean` | Delete build results |
| `./gradlew --stop` | Stop the background Gradle daemon (frees memory) |
| `./gradlew tasks` | List all tasks |
| `apksigner verify --print-certs file.apk` | Check the signature |
| `aapt2 dump badging file.apk` | Package, version, minSdk |
| `sdkmanager --list_installed` | What is installed in the SDK |

---

## 7. When something goes wrong

### `aapt2 … Syntax error` / `cannot execute binary file`
Gradle is trying to run the x86_64 `aapt2`.
Check that `~/.gradle/gradle.properties` has the `android.aapt2FromMavenOverride=…` line
and that the project's `gradle.properties` doesn't override it.

### `SDK location not found`
```sh
echo "sdk.dir=/opt/android-sdk" > local.properties
```

### `JAVA_HOME is not set` / `sdkmanager: not found`
The environment isn't loaded. Run:
```sh
. /etc/profile.d/android.sh
```

### The build hangs or fails with `OutOfMemoryError`
The phone is low on free memory.
- Close heavy apps on the phone
- Run `./gradlew --stop` and build again
- The Gradle memory limit is set in `~/.gradle/gradle.properties` (`-Xmx2g`)

### `Failed to find Build Tools revision X`
Change `buildToolsVersion` in `app/build.gradle.kts` to `"35.0.0"`.

### "App not installed" when installing the APK
- A version with a **different signature** is already installed: uninstall it
- The new APK's `versionCode` is lower than the installed one
- The project's `minSdk` is higher than the phone's Android version

---

## 8. GitHub and versions

> [!NOTE]
> **Already set up.**
> - Account: **Xtratter**, logged in (`gh auth status`)
> - Game: `/root/CatGame` → https://github.com/Xtratter/cat-hunt
> - Commit author: `Xtratter` with the GitHub noreply address (the gmail address stays private)
> - Changelog: `CHANGELOG.md` (English) and `CHANGELOG.ru.md` (Russian)

### Releasing a new version (example: 1.4)

1. Make the code changes and check that it builds: `./gradlew assembleDebug`
2. Bump the version in `app/build.gradle.kts`:
   ```kotlin
   versionCode = 6        // an integer, always +1
   versionName = "1.4"    // what people see
   ```
3. Add what's new at the top of `CHANGELOG.md` (in English), `CHANGELOG.ru.md` (in Russian) and in
   `fastlane/metadata/android/{ru-RU,en-US}/changelogs/<versionCode>.txt`
4. Build the release (with R8), sign it, save and publish:
   ```sh
   cd /root/CatGame
   ./gradlew assembleRelease
   BT=/opt/android-sdk/build-tools/35.0.0
   $BT/zipalign -f -p 4 app/build/outputs/apk/release/app-release-unsigned.apk /tmp/aligned.apk
   $BT/apksigner sign --ks ~/.config/.android/debug.keystore \
       --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey \
       --out ~/releases/CatHunt-v1.4.apk /tmp/aligned.apk
   cp ~/releases/CatHunt-v1.4.apk /sdcard/Download/
   git add -A
   git commit -m "Version 1.4: what changed"
   git tag -a v1.4 -m "Version 1.4"
   git push --follow-tags
   gh release create v1.4 ~/releases/CatHunt-v1.4.apk -t "Cat Hunt 1.4" -F notes.md
   ```
   `notes.md` holds the list of changes in English, then a `---` line, **Русский** and the same list in Russian.
5. Update the Obsidian copy of the Russian guide:
   ```sh
   cp docs/GUIDE.md "/sdcard/Download/Сборка Android-приложений.md"
   ```

> [!NOTE]
> **R8 is enabled for the release build** (`isMinifyEnabled`, `isShrinkResources`,
> rules in `app/proguard-rules.pro`). It removes unused code and resources
> and renames classes. F-Droid requires it. If reflection or serialization by class name
> is ever added, put `-keep` rules into `proguard-rules.pro`.
> After building, check on the phone that the game and the settings menu work.

> [!NOTE]
> **GitHub is kept in two languages.** `README.md`, `CHANGELOG.md` and release notes come in English first;
> the Russian versions are `README.ru.md` and `CHANGELOG.ru.md`. This guide has two files too:
> when you change `docs/GUIDE.md`, update `docs/GUIDE.en.md` as well.

> [!IMPORTANT]
> **`versionCode` only goes up.**
> Android won't install an APK over an installed version with a higher `versionCode`.

> [!CAUTION]
> **Keep the signing key.**
> All APKs are signed with `~/.config/.android/debug.keystore`. If it is lost,
> new versions won't install over old ones, and the game will have to be uninstalled first.
> A copy is already in `/sdcard/Download/debug.keystore.backup`; also move it to cloud storage.

> [!TIP]
> **Edited the guide in Obsidian?**
> Bring the changes back to the repository so they aren't lost:
> ```sh
> cp "/sdcard/Download/Сборка Android-приложений.md" /root/CatGame/docs/GUIDE.md
> cd /root/CatGame && git diff docs/GUIDE.md
> git commit -am "Guide: update" && git push
> ```

### Useful commands

| Command | What it does |
|---|---|
| `git status` | What has changed and isn't committed yet |
| `git log --oneline` | Version history |
| `git diff` | What exactly changed in the code |
| `gh release list` | List of GitHub releases |
| `gh repo view --web` | Link to the repository |
| `git checkout v1.0` | Look at the code of an old version (`git checkout main` to go back) |

---

## 9. Cat Hunt: how the code works

```
app/src/main/java/io/github/xtratter/cathunt/
├── MainActivity.kt   ← screen, fullscreen mode, settings menu, exit on double "Back"
├── GameView.kt       ← game loop, touches, flashes and sparks, who squeaks when
├── Critters.kt       ← all critters: Mouse, Roach, Rope, Butterfly, LaserDot, Fish
├── Sounds.kt         ← sound synthesis (squeak, rustle, chirp, psst, bloop, chime)
└── Settings.kt       ← saved settings
app/src/main/res/layout/activity_main.xml   ← settings menu layout
```

### Adding a new critter

1. In `Critters.kt`: a new class extending `Runner`. Set its speed, turns and pauses,
   and draw it in `draw()` (the critter faces right, along +x)
2. In `Settings.kt`: a flag `var name by flag("name")`, and add it to `rosterKey()`
3. In `GameView.rebuild()`: `if (settings.name) critters += Name(s, rnd)`
4. In `activity_main.xml` and `strings.xml`: a switch; in `MainActivity.setupSettings()`: `bindSwitch(...)`
5. A sound when it appears: in `GameView.callOut()`

### Settings menu

Opens by **pressing and holding** ⚙ in the top right corner (so a paw tap doesn't open it).
Settings are kept between launches.

---

## 10. F-Droid

> [!NOTE]
> **How it works.**
> F-Droid doesn't take a ready APK: its server downloads the code from GitHub, builds it and signs it
> with **its own** key. That's why the F-Droid version and the GitHub version can't be installed over each other:
> a user picks one source.

### What's already in the repository

| What | Where |
|---|---|
| GPL-3.0 license | `LICENSE` |
| Application ID | `io.github.xtratter.cathunt` |
| Catalog descriptions (ru, en) | `fastlane/metadata/android/<language>/` |
| 512×512 icon | `fastlane/metadata/android/en-US/images/icon.png` |
| Per-version changelog | `fastlane/metadata/android/<language>/changelogs/<versionCode>.txt` |
| F-Droid metadata | `docs/fdroid/io.github.xtratter.cathunt.yml` |

### The inclusion request

> [!NOTE]
> **Submitted on 2026-09-27:** [fdroiddata!50354](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/50354)
> (GitLab account **Xtratter**, logged in via `glab`).

To see the status and reviewer comments:
```sh
glab mr view 50354 -R fdroid/fdroiddata --comments
```

A daily check (10:07) updates the note `Статус заявки F-Droid.md` in Downloads.
The script is `/usr/local/bin/fdroid-mr-check`, the schedule is `/etc/cron.d/fdroid-mr-check`.
To check right now: `fdroid-mr-check`. If the phone was rebooted, `cron` starts
on the first terminal login (a line in `~/.bashrc`).

> [!TIP]
> **CI pipelines don't run: "user not being verified".**
> GitLab asks new accounts to verify their identity. F-Droid's rules say you don't have to:
> a comment in the request is enough, and the maintainers will run the pipelines.

> [!WARNING]
> **The GitLab token `cathunt` expires on 27.10.2026** (check: `glab api personal_access_tokens/self`).
> After that, create a new token
> (scopes `api`, `write_repository`) and log in again:
> `glab auth login --hostname gitlab.com --token glpat-…` (with the `glpat-` prefix!)

### A new version while the request is still open

The reviewers asked to keep the request up to date. After releasing version X.Y (section 8,
with the changelog files below), one command updates the request:

```sh
/root/fdroid-tools/update-mr.sh X.Y      # DRY=1 in front — check only, push nothing
```

It takes the `vX.Y` tag commit and versionCode, rewrites the metadata file, runs `fdroid lint`,
`rewritemeta` and the schema check with a fresh fdroidserver, commits the file to the fork branch
and copies it to `docs/fdroid/`. Then commit `docs/fdroid` here and post a short comment in the request.

### A new version after the app is in F-Droid

Everything from section 8, plus create the changelog files **before committing**:

```sh
# versionCode of the new version, for example 7
nano fastlane/metadata/android/ru-RU/changelogs/7.txt
nano fastlane/metadata/android/en-US/changelogs/7.txt
```

F-Droid notices the new `vX.Y` tag by itself and publishes the update within a few days.

> [!IMPORTANT]
> **Never delete or move published tags.**
> F-Droid builds versions from the tags.

### Screenshots

Put PNGs into `fastlane/metadata/android/en-US/images/phoneScreenshots/`
(named `1.png`, `2.png`, … in display order).

> [!CAUTION]
> **Don't add proprietary libraries to the project** (Google Play Services, Firebase, ads, analytics):
> F-Droid will reject the app or mark it with a warning (anti-feature).
