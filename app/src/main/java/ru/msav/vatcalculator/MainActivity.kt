package ru.msav.vatcalculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import ru.msav.vatcalculator.ui.screens.CalculatorScreen
import ru.msav.vatcalculator.ui.theme.VATCalculatorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VATCalculatorTheme {
                CalculatorScreen()
            }
        }
    }
}
