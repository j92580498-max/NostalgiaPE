# Инструкция по сборке

## Вариант 1 — Android Studio (рекомендуется)

1. Установите **Android Studio** (Giraffe или новее).
2. `File → Open` и выберите папку проекта **`NostalgiaPE`**.
3. Дождитесь синхронизации Gradle (Android Studio сам предложит поставить
   нужные компоненты SDK: **Android SDK Platform 34**, **Build-Tools 34.0.0**).
4. Выберите конфигурацию **app** и нажмите **Run ▶** (или `Shift+F10`).
5. Готовый отладочный APK окажется в
   `app/build/outputs/apk/debug/app-debug.apk`.

Никаких дополнительных модулей, NDK или C++ тулчейнов не требуется — проект
полностью на Java.

## Вариант 2 — командная строка

Требования:
- **JDK 17**
- **Android SDK** с компонентами `platforms;android-34` и
  `build-tools;34.0.0`
- Переменная окружения `ANDROID_HOME` (или `ANDROID_SDK_ROOT`), указывающая на
  SDK.

```bash
# из корня проекта
export ANDROID_HOME=/path/to/android-sdk

# отладочная сборка
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# релизная сборка (подписана debug-ключом для удобства установки)
./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

## Требования к устройству

- **Android 5.0 (API 21)** или новее.
- Поддержка **OpenGL ES 2.0** (есть практически на всех устройствах).
- Разрешение на доступ в интернет запрашивается автоматически (объявлено в
  манифесте).

## Установка APK на телефон

```bash
adb install -r dist/NostalgiaPE-1.0-debug.apk
```

Либо перекиньте `.apk` на устройство и откройте его файловым менеджером,
разрешив установку из неизвестных источников.

## Возможные проблемы

| Симптом | Решение |
|---|---|
| Gradle не находит SDK | Проверьте `ANDROID_HOME` / файл `local.properties` (`sdk.dir=...`). |
| «Duplicate class kotlin-stdlib» | Уже исправлено принудительной версией `kotlin-stdlib` в `app/build.gradle`. |
| Чёрный экран после входа | Сервер ещё не прислал чанки — подождите 2–5 секунд; статус вверху экрана покажет прогресс. |
| «Incompatible RakNet protocol» | Сервер использует иную версию RakNet — этот клиент рассчитан на классическое семейство PE (structure 5). |
