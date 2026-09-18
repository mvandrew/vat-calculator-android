package ru.msav.vatcalculator.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.msav.vatcalculator.BuildConfig
import ru.msav.vatcalculator.R
import ru.msav.vatcalculator.ui.appString

/**
 * «О программе» (interface.md §4.3): название, фактическая версия сборки,
 * автор, сайт, обратная связь и политика конфиденциальности. Веб-страницы
 * открываются браузером, письмо — внешним почтовым приложением; отсутствие
 * обработчика показывает сообщение без падения. Возврат сохраняет расчёт.
 *
 * Конечные адреса согласованы (задача раздела приложений msav.ru); RU/EN
 * используют один адрес, EN-версии страниц ожидаются к выпуску (фаза 07).
 */
object AboutLinks {
    const val SITE_URL = "https://msav.ru/apps/vat-calculator/"
    const val PRIVACY_URL = "https://msav.ru/apps/vat-calculator/privacy/"
    const val FEEDBACK_EMAIL = "info@msav.ru"
}

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var actionErrorText by remember { mutableStateOf<String?>(null) }
    val noBrowserText = appString(R.string.external_no_browser)
    val noEmailText = appString(R.string.external_no_email)
    val feedbackSubject = appString(R.string.about_feedback_subject)

    LaunchedEffect(actionErrorText) {
        actionErrorText?.let {
            snackbarHostState.showSnackbar(it)
            actionErrorText = null
        }
    }

    AboutScreenContent(
        versionName = BuildConfig.VERSION_NAME,
        onSite = {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AboutLinks.SITE_URL)))
            } catch (error: ActivityNotFoundException) {
                actionErrorText = noBrowserText
            }
        },
        onFeedback = {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(AboutLinks.FEEDBACK_EMAIL))
                putExtra(Intent.EXTRA_SUBJECT, feedbackSubject)
            }
            try {
                context.startActivity(intent)
            } catch (error: ActivityNotFoundException) {
                actionErrorText = noEmailText
            }
        },
        onPrivacy = {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AboutLinks.PRIVACY_URL)))
            } catch (error: ActivityNotFoundException) {
                actionErrorText = noBrowserText
            }
        },
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@Composable
fun AboutScreenContent(
    versionName: String,
    onSite: () -> Unit,
    onFeedback: () -> Unit,
    onPrivacy: () -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScreenHeader(title = appString(R.string.screen_about), onBack = onBack)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = appString(R.string.screen_calculator))
                Text(text = appString(R.string.about_version, versionName))
                Text(text = appString(R.string.about_author))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onSite) {
                    Text(appString(R.string.about_site))
                }
                TextButton(onClick = onFeedback) {
                    Text(appString(R.string.about_feedback))
                }
                TextButton(onClick = onPrivacy) {
                    Text(appString(R.string.about_privacy))
                }
            }
        }
    }
}
