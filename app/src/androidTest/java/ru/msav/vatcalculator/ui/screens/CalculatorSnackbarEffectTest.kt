package ru.msav.vatcalculator.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.msav.vatcalculator.calculation.AppLanguage
import ru.msav.vatcalculator.calculation.VatMode
import ru.msav.vatcalculator.ui.LocalAppLanguage
import ru.msav.vatcalculator.ui.theme.VATCalculatorTheme

@RunWith(AndroidJUnit4::class)
class CalculatorSnackbarEffectTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun leavingScreenWhileSaveSnackbarIsVisibleConsumesNotice() {
        var showScreen by mutableStateOf(true)
        var consumed = 0

        composeRule.setContent {
            VATCalculatorTheme {
                CompositionLocalProvider(LocalAppLanguage provides AppLanguage.ENGLISH) {
                    if (showScreen) {
                        CalculatorScreenContent(
                            state = CalculatorViewModel.UiState(
                                mode = VatMode.INCLUSIVE,
                                saveNotice = CalculatorViewModel.SaveNotice.SAVED,
                                loaded = true,
                            ),
                            onAmountChange = {},
                            onRateChange = {},
                            onModeChange = {},
                            onFieldFocusChanged = { _, _ -> },
                            onSave = {},
                            onNewCalculation = {},
                            onShare = {},
                            onConsumeShareText = {},
                            onConfirmDiscard = {},
                            onDismissDiscard = {},
                            onConsumeSaveNotice = { consumed += 1 },
                            onAcknowledgeMigrationNotice = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("Saved").assertExists()
        composeRule.runOnIdle { showScreen = false }
        composeRule.waitUntil(5_000) { consumed == 1 }

        assertEquals(1, consumed)
    }
}
