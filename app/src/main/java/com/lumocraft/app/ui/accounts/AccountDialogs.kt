package com.lumocraft.app.ui.accounts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.lumocraft.app.R
import com.lumocraft.app.domain.account.AccountValidator
import com.lumocraft.app.domain.account.OfflineAccount
import com.lumocraft.app.domain.account.UsernameError

/**
 * Create/rename dialog with live validation against [AccountValidator].
 * The confirm button stays disabled until the input is valid.
 */
@Composable
fun AccountNameDialog(
    title: String,
    initialValue: String,
    existingUsernames: Collection<String>,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(initialValue) { mutableStateOf(initialValue) }
    val validation = AccountValidator.validateUsername(name, existingUsernames)
    val focusRequester = remember { FocusRequester() }

    val supportingText = when (validation.error) {
        UsernameError.EMPTY -> null
        UsernameError.TOO_SHORT -> stringResource(R.string.error_username_too_short)
        UsernameError.TOO_LONG -> stringResource(R.string.error_username_too_long)
        UsernameError.INVALID_CHARACTERS -> stringResource(R.string.error_username_invalid)
        UsernameError.DUPLICATE -> stringResource(R.string.error_username_duplicate)
        null -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.accounts_username_label)) },
                placeholder = { Text(stringResource(R.string.accounts_username_hint)) },
                singleLine = true,
                isError = validation.error != null && name.isNotEmpty(),
                supportingText = supportingText?.let { message -> { Text(message) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { if (validation.isValid) onConfirm(name.trim()) }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = validation.isValid
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

/**
 * Delete confirmation. The confirm button is disabled while [canDelete] is
 * false (i.e. this is the last remaining account).
 */
@Composable
fun DeleteAccountDialog(
    account: OfflineAccount,
    canDelete: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text(stringResource(R.string.accounts_delete_title)) },
        text = {
            Column {
                Text(stringResource(R.string.accounts_delete_message, account.username))
                if (!canDelete) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.accounts_delete_last_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = canDelete
            ) {
                Text(
                    text = stringResource(R.string.accounts_delete),
                    color = if (canDelete) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
