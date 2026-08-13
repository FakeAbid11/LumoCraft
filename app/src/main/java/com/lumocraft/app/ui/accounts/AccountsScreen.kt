package com.lumocraft.app.ui.accounts

import android.text.format.DateUtils
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumocraft.app.R
import com.lumocraft.app.domain.account.CannotDeleteLastAccountException
import com.lumocraft.app.domain.account.DuplicateUsernameException
import com.lumocraft.app.domain.account.OfflineAccount
import kotlinx.coroutines.launch

private sealed interface AccountDialog {
    data object Create : AccountDialog
    data class Rename(val account: OfflineAccount) : AccountDialog
    data class Delete(val account: OfflineAccount) : AccountDialog
}

/**
 * Accounts section: lists local launcher profiles with avatars, selection,
 * creation, renaming and deletion.
 */
@Composable
fun AccountsScreen(
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel = viewModel(factory = AccountViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<AccountDialog?>(null) }

    fun showFailure(throwable: Throwable) {
        val message = when (throwable) {
            is DuplicateUsernameException -> context.getString(R.string.error_username_duplicate)
            is CannotDeleteLastAccountException -> context.getString(R.string.accounts_error_last_account)
            else -> context.getString(R.string.accounts_error_generic)
        }
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    dialog?.let { activeDialog ->
        when (activeDialog) {
            is AccountDialog.Create -> AccountNameDialog(
                title = stringResource(R.string.accounts_create_title),
                initialValue = "",
                existingUsernames = uiState.accounts.map { it.username },
                confirmLabel = stringResource(R.string.accounts_add),
                onConfirm = { name ->
                    viewModel.addAccount(name)
                        .onSuccess { dialog = null }
                        .onFailure { showFailure(it) }
                },
                onDismiss = { dialog = null }
            )

            is AccountDialog.Rename -> AccountNameDialog(
                title = stringResource(R.string.accounts_rename_title),
                initialValue = activeDialog.account.username,
                existingUsernames = uiState.accounts
                    .filter { it.id != activeDialog.account.id }
                    .map { it.username },
                confirmLabel = stringResource(R.string.accounts_save),
                onConfirm = { name ->
                    viewModel.renameAccount(activeDialog.account.id, name)
                        .onSuccess { dialog = null }
                        .onFailure { showFailure(it) }
                },
                onDismiss = { dialog = null }
            )

            is AccountDialog.Delete -> DeleteAccountDialog(
                account = activeDialog.account,
                canDelete = uiState.accounts.size > 1,
                onConfirm = {
                    viewModel.deleteAccount(activeDialog.account.id)
                        .onSuccess { dialog = null }
                        .onFailure { showFailure(it) }
                },
                onDismiss = { dialog = null }
            )
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = pluralStringResource(R.plurals.accounts_count, uiState.totalCount, uiState.totalCount),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Crossfade(
                targetState = uiState.accounts.isEmpty(),
                label = "accountsContent"
            ) { empty ->
                if (empty) {
                    EmptyAccountsState(modifier = Modifier.fillMaxSize())
                } else {
                    AccountsList(
                        accounts = uiState.accounts,
                        selectedAccountId = uiState.selectedAccount?.id,
                        onSelect = viewModel::selectAccount,
                        onRenameRequest = { dialog = AccountDialog.Rename(it) },
                        onDeleteRequest = { dialog = AccountDialog.Delete(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp)
            )

            ExtendedFloatingActionButton(
                onClick = { dialog = AccountDialog.Create },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                icon = { Icon(imageVector = Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.accounts_add_fab)) }
            )
        }
    }
}

@Composable
private fun AccountsList(
    accounts: List<OfflineAccount>,
    selectedAccountId: String?,
    onSelect: (String) -> Unit,
    onRenameRequest: (OfflineAccount) -> Unit,
    onDeleteRequest: (OfflineAccount) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(accounts, key = { it.id }) { account ->
            AccountCard(
                account = account,
                isSelected = account.id == selectedAccountId,
                onSelect = { onSelect(account.id) },
                onRenameRequest = { onRenameRequest(account) },
                onDeleteRequest = { onDeleteRequest(account) },
                modifier = Modifier.animateItem()
            )
        }
    }
}

@Composable
private fun AccountCard(
    account: OfflineAccount,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onRenameRequest: () -> Unit,
    onDeleteRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        onClick = onSelect,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AvatarView(
                username = account.username,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = account.username,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.accounts_selected),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.accounts_added_on,
                        DateUtils.getRelativeTimeSpanString(
                            account.createdAt,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS
                        )
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.accounts_menu)
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    if (!isSelected) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.accounts_select)) },
                            onClick = {
                                menuExpanded = false
                                onSelect()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.accounts_rename)) },
                        onClick = {
                            menuExpanded = false
                            onRenameRequest()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.accounts_delete),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDeleteRequest()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyAccountsState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        EmptyStateIllustration()
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.accounts_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.accounts_empty_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

/** Small pixel-art face drawn entirely with Compose — no image assets. */
@Composable
private fun EmptyStateIllustration(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val secondary = MaterialTheme.colorScheme.secondary

    Canvas(modifier = modifier.size(112.dp)) {
        val cell = this.size.minDimension / 8f
        EMPTY_STATE_PATTERN.forEachIndexed { index, shade ->
            if (shade > 0) {
                val color = when (shade) {
                    1 -> primary
                    2 -> tertiary
                    else -> secondary
                }
                drawRect(
                    color = color,
                    topLeft = Offset((index % 8) * cell, (index / 8) * cell),
                    size = Size(cell, cell)
                )
            }
        }
    }
}

private val EMPTY_STATE_PATTERN = intArrayOf(
    0, 0, 1, 1, 1, 1, 0, 0,
    0, 1, 1, 2, 2, 1, 1, 0,
    0, 1, 1, 2, 2, 1, 1, 0,
    0, 1, 1, 1, 1, 1, 1, 0,
    0, 0, 1, 1, 1, 1, 0, 0,
    0, 3, 0, 0, 0, 0, 3, 0,
    0, 0, 3, 3, 3, 3, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0
)
