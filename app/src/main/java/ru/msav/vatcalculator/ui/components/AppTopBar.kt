package ru.msav.vatcalculator.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import ru.msav.vatcalculator.R
import ru.msav.vatcalculator.ui.appString

/** Действие overflow-меню верхней панели: локализованная подпись и переход. */
class TopBarOverflowAction(val label: String, val onClick: () -> Unit)

/**
 * Единая компактная верхняя панель экранов (navigation-ui-modernization-task.md):
 * заголовок, необязательная стрелка возврата, необязательное действие журнала
 * и overflow-меню. Компонент stateless; описание и tooltip действий журнала и
 * меню локализуются через [appString], возврат получает описание от владельца
 * экрана («Назад в журнал» / «Назад к калькулятору»). Состояние открытия меню
 * краткоживущее и не переживает пересоздание экрана.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backContentDescription: String? = null,
    onOpenHistory: (() -> Unit)? = null,
    overflowActions: List<TopBarOverflowAction> = emptyList(),
) {
    TopAppBar(
        title = {
            Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        modifier = modifier,
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = backContentDescription,
                    )
                }
            }
        },
        actions = {
            if (onOpenHistory != null) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text(appString(R.string.nav_open_history)) } },
                    state = rememberTooltipState(),
                ) {
                    IconButton(onClick = onOpenHistory) {
                        Icon(
                            painter = painterResource(R.drawable.ic_history),
                            contentDescription = appString(R.string.nav_open_history),
                        )
                    }
                }
            }
            if (overflowActions.isNotEmpty()) {
                OverflowMenu(actions = overflowActions)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverflowMenu(actions: List<TopBarOverflowAction>) {
    var expanded by remember { mutableStateOf(false) }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(appString(R.string.nav_more_options)) } },
        state = rememberTooltipState(),
    ) {
        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_vert),
                    contentDescription = appString(R.string.nav_more_options),
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                actions.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(action.label) },
                        onClick = {
                            expanded = false
                            action.onClick()
                        },
                    )
                }
            }
        }
    }
}
