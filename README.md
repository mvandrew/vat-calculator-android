<p align="center">
  <img src="artwork/play/play-icon-512.png" alt="VAT Calculator app icon" width="128" height="128">
</p>

# VAT Calculator for Android

VAT Calculator is a native Android app for adding VAT to a net amount or extracting it from a gross amount. Calculations use precise decimal arithmetic, and saved entries remain editable.

[Product page](https://msav.ru/apps/vat-calculator/) · [Privacy policy](https://msav.ru/apps/vat-calculator/privacy/)

## Product highlights

- Add or remove VAT using any rate from 0% up to, but not including, 100%.
- Edit the net amount, VAT amount, or gross amount and recalculate the remaining values.
- Save calculations locally, reopen and update them, or remove them from history.
- Share a formatted result through the Android system share sheet.
- Switch between English and Russian without restarting the app.
- Follow the system appearance or select a light or dark theme.
- Keep calculation data on the device. The app has no analytics, advertising, or payment SDKs and does not request network access.

## Engineering profile

- The app is written in Kotlin with Jetpack Compose and Material 3.
- A ViewModel exposes lifecycle-aware state through `StateFlow`; coroutine-backed storage work stays off the main thread.
- `BigDecimal` arithmetic, explicit rounding, and locale-aware parsing keep monetary results deterministic.
- Local state and calculation history survive process restarts. One-time migration supports data created by versions 1.5 and 2.2.
- Release builds use code and resource shrinking. Debug tooling is excluded from release builds.

## Verification

The project includes 49 JVM tests covering input parsing, VAT arithmetic, reverse calculations, rounding, formatting, and legacy-data migration. The release process also checks lint, optimized builds, and behavior on an Android device.

## Author

Developed by [Andrey Mishchenko](https://msav.ru/).
