---
title: Сборка Android-приложений на телефоне
tags:
  - android
  - gradle
  - kotlin
  - инструкция
created: 2026-09-27
updated: 2026-09-28
---

# Сборка Android-приложений на телефоне

> [!NOTE]
> **Что это за окружение.**
> Ubuntu 24.04 работает в chroot на телефоне (POCO F3, ARM64).
> Всё нужное для сборки уже установлено, интернет нужен только для скачивания зависимостей.
>
> Этот файл живёт в репозитории [cat-hunt](https://github.com/Xtratter/cat-hunt) — `docs/GUIDE.md`,
> а его копия для Obsidian лежит в «Загрузках»: `Сборка Android-приложений.md`.
> Английская версия — `docs/GUIDE.en.md`.

## Что где лежит

| Что | Где |
|---|---|
| JDK 17 | `/usr/lib/jvm/java-17-openjdk-arm64` |
| Android SDK | `/opt/android-sdk` |
| Build-tools (ARM64) | `/opt/android-sdk/build-tools/35.0.0` |
| Gradle 8.10.2 | `/opt/gradle-8.10.2`, команда `gradle` |
| Переменные окружения | `/etc/profile.d/android.sh` |
| Глобальные настройки Gradle | `~/.gradle/gradle.properties` |
| Пример проекта | `/root/HelloAndroid` |
| Игра «Кошачья охота» | `/root/CatGame` |
| Монитор процессов DroidTop | `/root/DroidTop` → [github.com/Xtratter/droidtop](https://github.com/Xtratter/droidtop) |
| APK всех версий (игра и DroidTop) | `/root/releases` |
| Готовые APK для установки | `/sdcard/Download` |

> [!WARNING]
> **Главная особенность.**
> Официальные `aapt2`, `zipalign`, `adb` от Google собраны только под x86_64 и на телефоне **не запускаются**.
> Они заменены на ARM64-версии, а в `~/.gradle/gradle.properties` прописано:
> ```properties
> android.aapt2FromMavenOverride=/opt/android-sdk/build-tools/35.0.0/aapt2
> ```
> Эту строку не удаляйте — без неё любая сборка упадёт.

---

## 1. Быстрый старт: пересобрать пример

```sh
cd /root/HelloAndroid
./gradlew assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk /sdcard/Download/HelloAndroid.apk
```

Потом откройте файл в «Загрузках» и установите.

> [!TIP]
> **Первая сборка долгая.**
> Первый запуск скачивает зависимости и занимает ~1 минуту. Повторные сборки — заметно быстрее.

---

## 2. Новое приложение из примера

Самый простой путь — скопировать пример и переименовать.

```sh
cp -r /root/HelloAndroid /root/MyApp
cd /root/MyApp
rm -rf .gradle build app/build
```

Затем поменять имя пакета (например, на `com.me.myapp`):

1. В `settings.gradle.kts` — `rootProject.name = "MyApp"`
2. В `app/build.gradle.kts` — `namespace` и `applicationId` на `com.me.myapp`
3. Перенести код в новую папку пакета:
   ```sh
   mkdir -p app/src/main/java/com/me/myapp
   mv app/src/main/java/com/example/hello/* app/src/main/java/com/me/myapp/
   rm -r app/src/main/java/com/example
   sed -i 's/^package com.example.hello/package com.me.myapp/' app/src/main/java/com/me/myapp/*.kt
   ```
4. Название приложения на экране — в `app/src/main/res/values/strings.xml`

> [!NOTE]
> **Разный `applicationId` = разные приложения.**
> Если оставить `com.example.hello`, новое приложение установится **поверх** старого.

---

## 3. Структура проекта

```
MyApp/
├── settings.gradle.kts        ← имя проекта, репозитории
├── build.gradle.kts           ← версии плагинов (AGP, Kotlin)
├── gradlew                    ← запуск сборки
├── local.properties           ← sdk.dir=/opt/android-sdk
└── app/
    ├── build.gradle.kts       ← настройки приложения, зависимости
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/…/MainActivity.kt   ← код
        └── res/                     ← строки, картинки, разметка
```

### Добавить библиотеку

В `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
```

> [!WARNING]
> **AndroidX.**
> Для библиотек AndroidX добавьте в `gradle.properties` проекта:
> ```properties
> android.useAndroidX=true
> ```

---

## 4. Собрать чужой проект (из git)

```sh
cd /root
git clone https://github.com/автор/проект.git
cd проект
echo "sdk.dir=/opt/android-sdk" > local.properties
./gradlew assembleDebug
```

Если сборка падает — см. раздел 7 «Если что-то пошло не так».

> [!CAUTION]
> **Совместимость.**
> - Если в проекте указан другой `buildToolsVersion` — поменяйте на `"35.0.0"` или удалите строку.
> - Проекты с **нативным кодом** (C/C++, NDK, `externalNativeBuild`) пока не соберутся: нужен ARM64-NDK.
> - Нужна другая платформа (например, `compileSdk = 34`)? Установите её:
>   ```sh
>   sdkmanager "platforms;android-34"
>   ```

---

## 5. Релизная сборка (для распространения)

Debug-APK подписан тестовым ключом. Для публикации в магазинах нужен свой ключ.

### Создать ключ (один раз)

```sh
keytool -genkeypair -v -keystore /root/my-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias mykey
```

> [!CAUTION]
> **Берегите ключ.**
> Сделайте резервную копию `my-release.jks` и запомните пароль.
> Потеряете ключ — не сможете выпускать обновления приложения.

### Подключить ключ в `app/build.gradle.kts`

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("/root/my-release.jks")
            storePassword = "ПАРОЛЬ"
            keyAlias = "mykey"
            keyPassword = "ПАРОЛЬ"
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
> **Пароли не в публичный репозиторий.**
> Если проект лежит на GitHub, не пишите пароли прямо в `build.gradle.kts` —
> вынесите их в `~/.gradle/gradle.properties` (он не попадает в git).

### Собрать

```sh
./gradlew assembleRelease
cp app/build/outputs/apk/release/app-release.apk /sdcard/Download/MyApp.apk
```

---

## 6. Полезные команды

| Команда | Что делает |
|---|---|
| `./gradlew assembleDebug` | Собрать debug-APK |
| `./gradlew assembleRelease` | Собрать release-APK |
| `./gradlew lintDebug` | Проверить код и разметку на ошибки |
| `./gradlew clean` | Удалить результаты сборки |
| `./gradlew --stop` | Остановить фоновый Gradle (освобождает память) |
| `./gradlew tasks` | Список всех задач |
| `apksigner verify --print-certs файл.apk` | Проверить подпись |
| `aapt2 dump badging файл.apk` | Пакет, версия, minSdk |
| `sdkmanager --list_installed` | Что установлено в SDK |

---

## 7. Если что-то пошло не так

### `aapt2 … Syntax error` / `cannot execute binary file`
Gradle пытается запустить x86_64-версию `aapt2`.
Проверьте, что в `~/.gradle/gradle.properties` есть строка `android.aapt2FromMavenOverride=…`,
и что в `gradle.properties` проекта она не переопределена.

### `SDK location not found`
```sh
echo "sdk.dir=/opt/android-sdk" > local.properties
```

### `JAVA_HOME is not set` / `sdkmanager: not found`
Окружение не загружено. Выполните:
```sh
. /etc/profile.d/android.sh
```

### Сборка зависает или падает с `OutOfMemoryError`
На телефоне мало свободной памяти.
- Закройте тяжёлые приложения на телефоне
- `./gradlew --stop` и повторите сборку
- Лимит памяти Gradle задан в `~/.gradle/gradle.properties` (`-Xmx2g`)

### `Failed to find Build Tools revision X`
Поменяйте `buildToolsVersion` в `app/build.gradle.kts` на `"35.0.0"`.

### При установке APK: «Приложение не установлено»
- Уже установлена версия с **другой подписью** — удалите старую
- `versionCode` нового APK меньше, чем у установленного
- `minSdk` в проекте выше версии Android на телефоне

---

## 8. GitHub и версии

> [!NOTE]
> **Уже настроено.**
> - Аккаунт: **Xtratter**, вход выполнен (`gh auth status`)
> - Игра: `/root/CatGame` → https://github.com/Xtratter/cat-hunt
> - Автор коммитов — `Xtratter` со служебной почтой GitHub (gmail не светится)
> - История изменений — в `CHANGELOG.md` (английский) и `CHANGELOG.ru.md` (русский)

### Выпустить новую версию (пример: 1.4)

1. Внести изменения в код и проверить сборку: `./gradlew assembleDebug`
2. Поднять версию в `app/build.gradle.kts`:
   ```kotlin
   versionCode = 6        // целое число, всегда +1
   versionName = "1.4"    // то, что видит человек
   ```
3. Дописать, что нового, в начало `CHANGELOG.md` (по-английски), `CHANGELOG.ru.md` (по-русски) и в
   `fastlane/metadata/android/{ru-RU,en-US}/changelogs/<versionCode>.txt`
4. Собрать release (с R8), подписать, сохранить и опубликовать:
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
   В `notes.md` — сначала список изменений по-английски, потом строка `---`, **Русский** и тот же список по-русски.

5. Обновить копию этой инструкции для Obsidian:
   ```sh
   cp docs/GUIDE.md "/sdcard/Download/Сборка Android-приложений.md"
   ```

> [!NOTE]
> **R8 включён для release-сборки** (`isMinifyEnabled`, `isShrinkResources`,
> правила — в `app/proguard-rules.pro`). Он удаляет неиспользуемый код и ресурсы
> и переименовывает классы. Этого требует F-Droid. Если когда-нибудь появится
> рефлексия или сериализация по именам классов — добавьте `-keep`-правила в `proguard-rules.pro`.
> После сборки проверьте на телефоне, что игра и меню настроек работают.

> [!NOTE]
> **GitHub ведётся на двух языках.** `README.md`, `CHANGELOG.md` и описания релизов — сначала по-английски,
> русские версии — `README.ru.md` и `CHANGELOG.ru.md`. Эта инструкция тоже в двух файлах:
> меняете `docs/GUIDE.md` — поправьте и `docs/GUIDE.en.md`.

> [!IMPORTANT]
> **`versionCode` только растёт.**
> Android не установит APK поверх, если `versionCode` меньше, чем у уже установленной версии.

> [!CAUTION]
> **Сохраните ключ подписи.**
> Все APK подписаны ключом `~/.config/.android/debug.keystore`. Если он пропадёт,
> новые версии не встанут поверх старых — придётся удалять игру.
> Копия уже лежит в `/sdcard/Download/debug.keystore.backup` — перенесите её ещё и в облако.

> [!TIP]
> **Правили инструкцию в Obsidian?**
> Верните правки в репозиторий, чтобы не потерять:
> ```sh
> cp "/sdcard/Download/Сборка Android-приложений.md" /root/CatGame/docs/GUIDE.md
> cd /root/CatGame && git diff docs/GUIDE.md
> git commit -am "Инструкция: обновление" && git push
> ```

### Полезные команды

| Команда | Что делает |
|---|---|
| `git status` | Что изменено и ещё не сохранено |
| `git log --oneline` | История версий |
| `git diff` | Что именно изменилось в коде |
| `gh release list` | Список релизов на GitHub |
| `gh repo view --web` | Ссылка на репозиторий |
| `git checkout v1.0` | Посмотреть код старой версии (`git checkout main` — вернуться) |

---

## 9. «Кошачья охота»: как устроен код

```
app/src/main/java/io/github/xtratter/cathunt/
├── MainActivity.kt   ← экран, полноэкранный режим, меню настроек, выход по двойному «Назад»
├── GameView.kt       ← игровой цикл, касания, вспышки и искры, кто когда пищит
├── Critters.kt       ← все зверьки: Mouse, Roach, Rope, Butterfly, LaserDot, Fish
├── Sounds.kt         ← синтез звуков (писк, шуршание, щебет, пс-пс, бульк, звон)
└── Settings.kt       ← сохранённые настройки
app/src/main/res/layout/activity_main.xml   ← разметка меню настроек
```

### Добавить нового зверька

1. В `Critters.kt` — новый класс, наследник `Runner`. Задать скорость, повороты, паузы
   и нарисовать его в `draw()` (зверёк смотрит вправо, по оси +x)
2. В `Settings.kt` — флаг `var имя by flag("имя")` и добавить его в `rosterKey()`
3. В `GameView.rebuild()` — `if (settings.имя) critters += Имя(s, rnd)`
4. В `activity_main.xml` и `strings.xml` — переключатель, в `MainActivity.setupSettings()` — `bindSwitch(...)`
5. Звук при появлении — в `GameView.callOut()`

### Меню настроек

Открывается **удержанием** ⚙ в правом верхнем углу (от случайного нажатия лапой).
Настройки сохраняются между запусками.

---

## 10. F-Droid

> [!NOTE]
> **Как это устроено.**
> F-Droid не берёт готовый APK: его сервер сам скачивает код с GitHub, собирает и подписывает
> **своим** ключом. Поэтому версия из F-Droid и версия с GitHub не ставятся друг поверх друга —
> пользователь выбирает один источник.

### Что уже готово в репозитории

| Что | Где |
|---|---|
| Лицензия GPL-3.0 | `LICENSE` |
| Идентификатор | `io.github.xtratter.cathunt` |
| Описания для каталога (ru, en) | `fastlane/metadata/android/<язык>/` |
| Иконка 512×512 | `fastlane/metadata/android/en-US/images/icon.png` |
| Список изменений версии | `fastlane/metadata/android/<язык>/changelogs/<versionCode>.txt` |
| Заявка для F-Droid | `docs/fdroid/io.github.xtratter.cathunt.yml` |

### Заявка

> [!NOTE]
> **Отправлена 27.09.2026:** [fdroiddata!50354](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/50354)
> (аккаунт GitLab — **Xtratter**, вход через `glab`).

Посмотреть статус и комментарии рецензентов:
```sh
glab mr view 50354 -R fdroid/fdroiddata --comments
```

Ежедневная проверка (10:07) обновляет заметку `Статус заявки F-Droid.md` в «Загрузках».
Она работает в Termux: скрипт — `~/projects/tools/fdroid-mr-check.py` (команда `fdroid-mr-check`),
расписание — `crontab -l`. Проверить прямо сейчас: `fdroid-mr-check`. Если Android выгрузил Termux
и 10:07 прошло, проверка выполнится при открытии Termux (строки в `~/.bashrc` запускают `crond` и догоняют).
После нашего ответа в заявке: `fdroid-mr-check --note "что сделали"` — строка в истории.
Проверка идёт через `glab`, а если он не вошёл — читает открытую заявку без токена.

> [!TIP]
> **Проверки (CI) не запускаются — «user not being verified».**
> GitLab требует подтвердить личность новых аккаунтов. По правилам F-Droid подтверждать не нужно —
> достаточно комментария в заявке, проверки запустят мейнтейнеры.

> [!NOTE]
> **glab вошёл по коду устройства, а не по токену** — продлевать ничего не нужно, glab сам обновляет доступ.
> Войти заново (пакет `glab-cli` в Termux): `glab auth login --hostname gitlab.com --device --git-protocol https`,
> затем ввести показанный код на https://gitlab.com/oauth/device. Проверить: `glab auth status`.

### Новая версия, пока заявка ещё открыта

Рецензенты попросили держать заявку в актуальном состоянии. После выпуска версии X.Y (раздел 8,
вместе с файлами списка изменений, см. ниже) заявка обновляется одной командой:

```sh
update-mr X.Y            # DRY=1 update-mr X.Y — только проверить, ничего не отправлять
```

Скрипт берёт коммит тега `vX.Y` и versionCode, переписывает файл метаданных, прогоняет `fdroid lint`,
`rewritemeta` и проверку по схеме свежим fdroidserver, коммитит файл в ветку форка и копирует его
в `docs/fdroid/`. Инструменты лежат в `~/projects/fdroid-tools/` (fdroidserver master в отдельном
окружении Python `venv`, схема и категории fdroiddata). Потом закоммитьте `docs/fdroid` здесь и напишите короткий комментарий в заявке.

### Новая версия после попадания в F-Droid

Всё то же, что в разделе 8, плюс **перед коммитом** создать файлы списка изменений:

```sh
# versionCode новой версии, например 7
nano fastlane/metadata/android/ru-RU/changelogs/7.txt
nano fastlane/metadata/android/en-US/changelogs/7.txt
```

F-Droid сам заметит новый тег `vX.Y` и через несколько дней выпустит обновление.

> [!IMPORTANT]
> **Никогда не удаляйте и не переставляйте опубликованные теги.**
> F-Droid собирает версии именно по тегам.

### Скриншоты

Кладите PNG в `fastlane/metadata/android/en-US/images/phoneScreenshots/`
(имена `1.png`, `2.png`, … — порядок показа).

> [!CAUTION]
> **Нельзя добавлять в проект закрытые библиотеки** (Google Play Services, Firebase, реклама, аналитика) —
> F-Droid откажет или пометит приложение предупреждением (anti-feature).
