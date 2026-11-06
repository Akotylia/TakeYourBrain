package com.takeyourbrain.app.ui.permissions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.takeyourbrain.app.R
import com.takeyourbrain.app.ui.common.AppAlertDialog

/**
 * Объяснение перед включением службы специальных возможностей. Этого требует Google Play: отдельное окно
 * внутри приложения — какие данные видит служба и зачем — и явное согласие кнопкой.
 * Показываем каждый раз перед переходом в настройки: служба выключена редко, а согласие должно быть осознанным.
 */
@Composable
fun AccessibilityDisclosureDialog(onAccept: () -> Unit, onDecline: () -> Unit) {
    AppAlertDialog(
        onDismissRequest = onDecline,
        title = { Text(stringResource(R.string.disclosure_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.disclosure_text))
            }
        },
        confirmButton = { Button(onClick = onAccept) { Text(stringResource(R.string.disclosure_accept)) } },
        dismissButton = { TextButton(onClick = onDecline) { Text(stringResource(R.string.disclosure_decline)) } }
    )
}
