package ru.msav.vatcalculator.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import ru.msav.vatcalculator.R
import ru.msav.vatcalculator.ui.appString

/**
 * Одноразовая отправка текста (interface.md §7): text/plain через системный
 * Sharesheet. Отмена и отсутствие обработчика не меняют расчёт и не вызывают
 * падения. Эффект общий для калькулятора и журнала: экраны взаимоисключающие,
 * поэтому одноразовое событие поглощает активный потребитель.
 */
@Composable
internal fun ShareSheetEffect(
    shareText: String?,
    snackbarHostState: SnackbarHostState,
    onConsume: () -> Unit,
) {
    val context = LocalContext.current
    val shareNoAppText = appString(R.string.share_no_app)
    val chooserTitle = appString(R.string.share_via)

    LaunchedEffect(shareText) {
        val text = shareText
        if (text != null) {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            val chooser = Intent.createChooser(sendIntent, chooserTitle)
            try {
                context.startActivity(chooser)
            } catch (error: ActivityNotFoundException) {
                snackbarHostState.showSnackbar(shareNoAppText)
            }
        }
        onConsume()
    }
}
