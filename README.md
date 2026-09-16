# Калькулятор НДС — Android

Существующая заготовка с обновлёнными зависимостями. Реализация приложения, Activity, переход XML-тем на Compose, идентификатор выпуска, backup и release signing выполняются отдельной задачей по ТЗ workspace `tasks/01-android-release/technical-specification.md`.

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

Встроенный Kotlin AGP сохранён; KGP обновляется через buildscript classpath без `org.jetbrains.kotlin.android`. Compose Compiler и зависимости подключены для предстоящей реализации ТЗ. AppCompat и Material Components пока нужны существующим XML-темам; их удаление относится к переходу UI на Compose. Debug tooling не входит в release.

Стартовой Activity и кода калькулятора пока нет. Существующий шаблонный unit-тест не подтверждает расчёты. `applicationId = ru.msav.vatcalculator` и версии `1 / 1.0` остаются исходными техническими значениями, не конфигурацией выпуска в существующую карточку Google Play.

## Проверка обновления — 16 сентября 2026

Debug/release APK, release AAB, компиляция instrumentation-тестов, существующий unit-тест и оба Lint прошли на JBR 25.0.3. Проверка относится к заготовке с обновлёнными зависимостями; готовность UI и выполнение фаз ТЗ не подтверждаются. Исходники, manifest, ресурсы и тесты в рамках итогового изменения не изменены.
