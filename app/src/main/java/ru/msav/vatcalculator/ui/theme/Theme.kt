package ru.msav.vatcalculator.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import ru.msav.vatcalculator.storage.ThemeSetting
import ru.msav.vatcalculator.ui.resolveTheme

@Composable
fun VATCalculatorTheme(
    themeSetting: ThemeSetting = ThemeSetting.SYSTEM,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        if (resolveTheme(themeSetting, isSystemInDarkTheme())) darkColorScheme() else lightColorScheme()
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
