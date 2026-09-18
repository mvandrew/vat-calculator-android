# Калькулятор НДС — Android

Новая реализация по ТЗ workspace `tasks/01-android-release/technical-specification.md`. Реализованы расчётное ядро, локальное состояние и миграция данных прежних версий, журнал, четыре Compose-экрана, RU/EN, темы, отправка и внешние ссылки. Release-подготовка ведётся в фазе 07.

## Сборка

- Android Studio Quail 4 (2026.1.4), встроенный JBR 25.0.3.
- Gradle Wrapper 9.7.1, AGP 9.4.0, Kotlin и Compose Compiler 2.4.20.
- JVM Gradle: 25 (закреплена без локальных путей); Java/Kotlin target: 17.
- SDK Platform 37.2, Build Tools 37.0.0; minSdk 24, targetSdk 36.
- Версии библиотек: `gradle/libs.versions.toml`, Compose через BOM 2026.09.00.

В Android Studio выберите встроенный JBR как Gradle JDK. SDK задаётся локальным `local.properties` (`sdk.dir`) либо через `ANDROID_HOME`; машинные пути не коммитятся. Для CLI на macOS:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew --version
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleRelease :app:bundleRelease :app:lintRelease
```

Постоянный автоматический контур — 49 JVM-тестов: 45 проверок расчёта, ввода, округления, обратных пересчётов от итогов и форматирования и четыре временные проверки миграции до завершения обновления 2.2 → 3.0. Instrumentation-тестов в проекте нет; перед выпуском выполняется короткая ручная smoke-проверка, а миграция подтверждается реальным обновлением через тестовый трек Google Play.

Kotlin 2.4.20 используется с более новыми AGP/Gradle, чем верхняя граница его таблицы полной совместимости. Версии выбраны явно и проверяются сборкой и lint; предупреждения несовместимости не подавляются.

Встроенный Kotlin AGP сохранён; KGP обновляется через buildscript classpath без `org.jetbrains.kotlin.android`. Аналитических, рекламных и платёжных SDK нет. Debug tooling не входит в release.

## Конфигурация выпуска

- `applicationId = ru.msav.ruvattaxcalculator` — пакет существующей карточки Google Play (ТЗ, идентичность выпуска).
- Внутренний `namespace = ru.msav.vatcalculator` сохранён; классы остаются в этом пакете.
- `versionName = 3.0`, `versionCode = 13` — актуальный код для следующей загрузки в Play Console; коды 10–12 использованы при подготовке выпусков в Console (максимальный код версии 2.2 — 9).
- Release использует R8: `isMinifyEnabled = true`, `isShrinkResources = true`, базовый файл `proguard-android-optimize.txt`; прикладные keep rules в `app/proguard-rules.pro` отсутствуют. Mapping-файл `app/build/outputs/mapping/release/mapping.txt` сохраняется для каждой выпущенной версии и нужен для расшифровки stack traces.
- Пароли и закрытый upload key не сохраняются в проекте. Финальный AAB владелец подписывает вручную через Android Studio; загрузка и публикация выполняются отдельными фазами.

## Структура

- `MainActivity` и `ui/AppNavigation.kt` — launcher Activity, тема, локализация и навигация четырёх Compose-экранов.
- `calculation/` — разбор ввода, точные расчёты, форматирование и текст отправки.
- `storage/` — последнее состояние, журнал и однократная миграция данных версий 1.5/2.2.
- `ui/screens/` — калькулятор, журнал, настройки и экран «О программе».
- `artwork/` и launcher-ресурсы — утверждённый языково-нейтральный набор значков для приложения и Google Play.

XML-темы MaterialComponents, AppCompat и Material Components удалены при переходе UI на Compose. Проверка обновления заготовки от 16 сентября 2026 к прежнему составу зависимостей не применяется к текущей конфигурации; актуальные результаты проверок — в отчёте фазы 02 workspace.
