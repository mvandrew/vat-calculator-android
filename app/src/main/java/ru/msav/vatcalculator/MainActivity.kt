package ru.msav.vatcalculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import ru.msav.vatcalculator.ui.screens.CalculatorScreen
import ru.msav.vatcalculator.ui.screens.CalculatorViewModel
import ru.msav.vatcalculator.ui.theme.VATCalculatorTheme

class MainActivity : ComponentActivity() {

    private val viewModel: CalculatorViewModel by viewModels { CalculatorViewModel.factory(application) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VATCalculatorTheme {
                CalculatorScreen(viewModel = viewModel)
            }
        }
    }
}
