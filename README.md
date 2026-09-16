# Калькулятор НДС — Android

Новая реализация по ТЗ workspace `tasks/01-android-release/technical-specification.md`. Выполнена фаза 02: запускаемый Compose-проект. Расчётное ядро, хранение, миграция и рабочие экраны реализуются в следующих фазах.

## Сборка

- Android Studio Quail 4 (2026.1.4), встроенный JBR 25.0.3.
- Gradle Wrapper 9.7.1, AGP 9.4.0, Kotlin и Compose Compiler 2.4.20.
- JVM Gradle: 25 (закреплена без локальных путей); Java/Kotlin target: 17.
- SDK Platform 37.2, Build Tools 37.0.0; minSdk 24, targetSdk 37.
- Версии библиотек: `gradle/libs.versions.toml`, Compose через BOM 2026.09.00.

В Android Studio выберите встроенный JBR как Gradle JDK. SDK задаётся локальным `local.properties` (`sdk.dir`) либо через `ANDROID_HOME`; машинные пути не коммитятся. Для CLI на macOS:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew --version
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
./gradlew :app:assembleRelease :app:bundleRelease :app:lintRelease
```

Kotlin 2.4.20 используется с более новыми AGP/Gradle, чем верхняя граница его таблицы полной совместимости. Версии выбраны явно и проверяются сборкой и lint; предупреждения несовместимости не подавляются.

Встроенный Kotlin AGP сохранён; KGP обновляется через buildscript classpath без `org.jetbrains.kotlin.android`. Аналитических, рекламных и платёжных SDK нет. Debug tooling не входит в release.

## Конфигурация выпуска

- `applicationId = ru.msav.ruvattaxcalculator` — пакет существующей карточки Google Play (ТЗ, идентичность выпуска).
- Внутренний `namespace = ru.msav.vatcalculator` сохранён; классы остаются в этом пакете.
- `versionName = 3.0` — пользовательская версия нового выпуска. `versionCode = 1` — техническое значение; окончательный код назначается в фазе 07 после сверки всех кодов в Play Console.
- Подписание release и публикация не настроены; выполняются в фазе 07.

## Структура (фаза 02)

- `MainActivity` — launcher Activity, Compose `setContent`.
- `ui/theme/Theme.kt` — Material 3, светлая/тёмная схема по системной теме.
- `ui/screens/` — каркасы четырёх экранов: `CalculatorScreen`, `HistoryScreen`, `SettingsScreen`, `AboutScreen` (заглушки без бизнес-логики).
- Пакеты расчётного ядра, состояния и журнала появятся в фазах 03–04 вместе с реализацией.

XML-темы MaterialComponents, AppCompat и Material Components удалены при переходе UI на Compose. Проверка обновления заготовки от 16 сентября 2026 к прежнему составу зависимостей не применяется к текущей конфигурации; актуальные результаты проверок — в отчёте фазы 02 workspace.
