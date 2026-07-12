package com.yage.opencode_client.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yage.opencode_client.R
import com.yage.opencode_client.data.model.BasicAuthConfig
import com.yage.opencode_client.data.model.HostProfile
import com.yage.opencode_client.data.model.HostTransport
import com.yage.opencode_client.data.model.SshTunnelConfig
import com.yage.opencode_client.ui.MainViewModel
import com.yage.opencode_client.ui.theme.uiScaled
import java.util.UUID

/**
 * Host Profiles settings section.
 *
 * Additive: this sits ABOVE the legacy [ServerConnectionSection] in the settings screen so users
 * see profile-based management first, while the legacy direct-connection form remains available
 * below for backward compatibility.
 */
@Composable
internal fun HostProfilesSection(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val profiles = state.hostProfiles
    val currentId = state.currentHostProfileId

    // Local UI state for the editor dialog and confirm dialogs.
    var editing by remember { mutableStateOf<HostProfile?>(null) }
    var creating by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<HostProfile?>(null) }
    var exportedPayload by remember { mutableStateOf<String?>(null) }
    var exportName by remember { mutableStateOf<String?>(null) }

    SectionHeader(title = stringResource(R.string.host_profiles_title))

    if (profiles.isEmpty()) {
        Text(
            text = stringResource(R.string.host_profiles_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(8.dp.uiScaled()))
    }

    profiles.forEach { profile ->
        HostProfileRow(
            profile = profile,
            isSelected = profile.id == currentId,
            onSelect = { viewModel.selectHostProfile(profile.id) },
            onEdit = { editing = profile },
            onDuplicate = { viewModel.duplicateHostProfile(profile.id) },
            onDelete = { pendingDelete = profile },
            onExport = {
                exportName = profile.displayName
                exportedPayload = viewModel.exportHostProfile(profile)
            }
        )
        Spacer(modifier = Modifier.height(8.dp.uiScaled()))
    }

    OutlinedButton(
        onClick = { creating = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp.uiScaled()))
        Text(stringResource(R.string.host_profiles_add))
    }

    // New-profile editor
    if (creating) {
        HostProfileEditorDialog(
            initial = null,
            onDismiss = { creating = false },
            onSave = { profile, password ->
                viewModel.saveHostProfile(profile, password)
                creating = false
            }
        )
    }

    // Edit existing profile
    editing?.let { target ->
        HostProfileEditorDialog(
            initial = target,
            onDismiss = { editing = null },
            onSave = { profile, password ->
                viewModel.saveHostProfile(profile, password)
                editing = null
            }
        )
    }

    // Delete confirmation
    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.host_profiles_delete_title)) },
            text = { Text(stringResource(R.string.host_profiles_delete_confirm, target.displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteHostProfile(target.id)
                    pendingDelete = null
                }) { Text(stringResource(R.string.host_profiles_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // Export result
    exportedPayload?.let { payload ->
        ExportPayloadDialog(
            profileName = exportName ?: "profile",
            payload = payload,
            onDismiss = {
                exportedPayload = null
                exportName = null
            }
        )
    }
}

@Composable
private fun HostProfileRow(
    profile: HostProfile,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        shape = RoundedCornerShape(12.dp.uiScaled())
    ) {
        Row(
            modifier = Modifier
                .clickable { onSelect() }
                .fillMaxWidth()
                .padding(horizontal = 12.dp.uiScaled(), vertical = 10.dp.uiScaled()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = transportIcon(profile.transport),
                contentDescription = null,
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(20.dp.uiScaled())
            )
            Spacer(modifier = Modifier.width(12.dp.uiScaled()))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp.uiScaled()))
                    Text(
                        text = transportLabel(profile.transport),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        modifier = Modifier
                            .background(
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                                shape = RoundedCornerShape(6.dp.uiScaled())
                            )
                            .padding(horizontal = 6.dp.uiScaled(), vertical = 2.dp.uiScaled())
                    )
                }
                Spacer(modifier = Modifier.height(2.dp.uiScaled()))
                Text(
                    text = profile.connectionSummary.ifBlank { "—" },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
            }
            Spacer(modifier = Modifier.width(4.dp.uiScaled()))
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp.uiScaled())
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.host_profiles_more_options),
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.host_profiles_edit)) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.host_profiles_duplicate)) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDuplicate()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.host_profiles_export)) },
                        leadingIcon = { Icon(Icons.Default.IosShare, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onExport()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.host_profiles_delete)) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportPayloadDialog(
    profileName: String,
    payload: String,
    onDismiss: () -> Unit
) {
    @Suppress("DEPRECATION")
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.host_profiles_export_title, profileName)) },
        text = {
            Column {
                Text(
                    text = "Copy this JSON and import it on another device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(8.dp.uiScaled()))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(8.dp.uiScaled()),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = payload,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .padding(8.dp.uiScaled())
                            .heightIn(max = 240.dp.uiScaled())
                            .verticalScroll(rememberScrollState())
                    )
                }
                if (copied) {
                    Spacer(modifier = Modifier.height(8.dp.uiScaled()))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp.uiScaled())
                        )
                        Spacer(modifier = Modifier.width(4.dp.uiScaled()))
                        Text(
                            text = stringResource(R.string.ssh_key_copied_toast),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(payload))
                copied = true
            }) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp.uiScaled()))
                Spacer(modifier = Modifier.width(4.dp.uiScaled()))
                Text(if (copied) stringResource(R.string.ssh_key_copied) else stringResource(R.string.ssh_key_copy))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        }
    )
}

/**
 * Create/edit dialog for a [HostProfile]. Pass [initial] = null to create a new profile.
 */
@Composable
internal fun HostProfileEditorDialog(
    initial: HostProfile?,
    onDismiss: () -> Unit,
    onSave: (HostProfile, String?) -> Unit
) {
    val isNew = initial == null
    val existing = initial

    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var transport by remember { mutableStateOf(existing?.transport ?: HostTransport.DIRECT) }
    var serverUrl by remember {
        mutableStateOf(existing?.serverUrl ?: defaultServerUrl(transport))
    }
    var username by remember { mutableStateOf(existing?.basicAuth?.username.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var hasBasicAuth by remember { mutableStateOf(existing?.basicAuth != null) }

    var sshHost by remember { mutableStateOf(existing?.ssh?.host.orEmpty()) }
    var sshPort by remember {
        mutableStateOf((existing?.ssh?.port ?: 8006).toString())
    }
    var sshUsername by remember { mutableStateOf(existing?.ssh?.username.orEmpty()) }
    var remotePort by remember {
        mutableStateOf((existing?.ssh?.remotePort ?: 19001).toString())
    }

    val nameError = name.trim().isEmpty()
    val urlError = transport != HostTransport.SSH_TUNNEL && serverUrl.trim().isEmpty()
    val portError = sshPort.toIntOrNull()?.let { it <= 0 } ?: true
    val remotePortError = remotePort.toIntOrNull()?.let { it <= 0 } ?: true
    val sshHostError = transport == HostTransport.SSH_TUNNEL && sshHost.trim().isEmpty()
    val canSave = !nameError &&
        !urlError &&
        !portError &&
        !remotePortError &&
        !sshHostError

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) stringResource(R.string.host_profile_new) else stringResource(R.string.host_profile_edit_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.host_profile_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text(stringResource(R.string.host_profile_name_required)) }
                    } else null
                )

                Spacer(modifier = Modifier.height(12.dp.uiScaled()))

                Text(
                    text = "Transport",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp.uiScaled()))
                TransportSelector(
                    selected = transport,
                    onSelect = {
                        transport = it
                        // Reset server URL to a sensible default when switching into SSH mode.
                        if (it == HostTransport.SSH_TUNNEL &&
                            (serverUrl.isBlank() || serverUrl.startsWith("http://localhost"))
                        ) {
                            serverUrl = defaultServerUrl(it)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp.uiScaled()))

                // Server URL field: only show for DIRECT mode. In SSH_TUNNEL mode the
                // connection URL is the local tunnel port (auto-assigned), so this field
                // is irrelevant and showing it causes confusion.
                if (transport != HostTransport.SSH_TUNNEL) {
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = {
                            Text(stringResource(R.string.host_profile_server_url))
                        },
                        placeholder = { Text(defaultServerUrl(transport)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = urlError,
                        supportingText = if (urlError) {
                            { Text(stringResource(R.string.host_profile_server_url_required)) }
                        } else null
                    )

                    Spacer(modifier = Modifier.height(12.dp.uiScaled()))
                }

                Spacer(modifier = Modifier.height(12.dp.uiScaled()))

                // Basic Auth
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Basic Authentication",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = { hasBasicAuth = !hasBasicAuth }) {
                        Text(if (hasBasicAuth) stringResource(R.string.host_profile_auth_remove) else stringResource(R.string.host_profile_auth_add))
                    }
                }
                if (hasBasicAuth) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.host_profile_username)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp.uiScaled()))
                    var showPw by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.host_profile_password)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPw = !showPw }) {
                                Icon(
                                    if (showPw) Icons.Default.Refresh else Icons.Default.Key,
                                    contentDescription = if (showPw) "Hide password" else "Show password"
                                )
                            }
                        }
                    )
                }

                if (transport == HostTransport.SSH_TUNNEL) {
                    Spacer(modifier = Modifier.height(16.dp.uiScaled()))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(8.dp.uiScaled()),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp.uiScaled())) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.VpnKey,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp.uiScaled())
                                )
                                Spacer(modifier = Modifier.width(8.dp.uiScaled()))
                                Text(
                                    text = "SSH Tunnel",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp.uiScaled()))
                            OutlinedTextField(
                                value = sshHost,
                                onValueChange = { sshHost = it },
                                label = { Text(stringResource(R.string.host_profile_ssh_gateway)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                isError = sshHostError,
                                supportingText = if (sshHostError) {
                                    { Text(stringResource(R.string.host_profile_ssh_required)) }
                                } else null
                            )
                            Spacer(modifier = Modifier.height(8.dp.uiScaled()))
                            OutlinedTextField(
                                value = sshPort,
                                onValueChange = { sshPort = it.filter { c -> c.isDigit() } },
                                label = { Text(stringResource(R.string.host_profile_ssh_port)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                isError = portError,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = KeyboardType.Number
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp.uiScaled()))
                            OutlinedTextField(
                                value = sshUsername,
                                onValueChange = { sshUsername = it },
                                label = { Text(stringResource(R.string.host_profile_ssh_username)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(8.dp.uiScaled()))
                            OutlinedTextField(
                                value = remotePort,
                                onValueChange = { remotePort = it.filter { c -> c.isDigit() } },
                                label = { Text(stringResource(R.string.host_profile_remote_port)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                isError = remotePortError,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = KeyboardType.Number
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!canSave) return@Button
                    val basicAuth = if (hasBasicAuth && username.isNotBlank()) {
                        BasicAuthConfig(
                            username = username.trim(),
                            // passwordId is normalized by viewModel.saveHostProfile() to profile.id
                            passwordId = existing?.id ?: UUID.randomUUID().toString()
                        )
                    } else {
                        null
                    }
                    val ssh = if (transport == HostTransport.SSH_TUNNEL) {
                        SshTunnelConfig(
                            host = sshHost.trim(),
                            port = sshPort.toIntOrNull() ?: 8006,
                            username = sshUsername.trim().ifBlank { "opencode" },
                            remotePort = remotePort.toIntOrNull() ?: 19001
                        )
                    } else {
                        null
                    }
                    val profile = (existing ?: HostProfile(
                        id = UUID.randomUUID().toString(),
                        name = name.trim(),
                        transport = transport,
                        serverUrl = serverUrl.trim(),
                        basicAuth = basicAuth,
                        ssh = ssh
                    )).copy(
                        name = name.trim(),
                        transport = transport,
                        serverUrl = serverUrl.trim(),
                        basicAuth = basicAuth,
                        ssh = ssh
                    )
                    val pw = password.takeIf { it.isNotBlank() }
                    onSave(profile, pw)
                },
                enabled = canSave
            ) { Text(stringResource(R.string.host_profile_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TransportSelector(
    selected: HostTransport,
    onSelect: (HostTransport) -> Unit
) {
    val options = listOf(HostTransport.DIRECT, HostTransport.SSH_TUNNEL)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, transport ->
            SegmentedButton(
                selected = transport == selected,
                onClick = { onSelect(transport) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = transportIcon(transport),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp.uiScaled())
                        )
                        Spacer(modifier = Modifier.width(6.dp.uiScaled()))
                        Text(transportLabel(transport))
                    }
                }
            )
        }
    }
}

/**
 * Shows the device's SSH public key, with copy and rotate actions.
 */
@Composable
internal fun SshPublicKeySection(viewModel: MainViewModel) {
    var publicKey by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }
    var showRotateConfirm by remember { mutableStateOf(false) }

    // Lazily ensure + load the public key when this section renders.
    LaunchedEffect(Unit) {
        publicKey = viewModel.ensureSshPublicKey()
    }

    SectionHeader(title = stringResource(R.string.ssh_key_title))

    publicKey?.let { key ->
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(8.dp.uiScaled()),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = key,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .padding(12.dp.uiScaled())
                    .heightIn(max = 160.dp.uiScaled())
                    .verticalScroll(rememberScrollState())
            )
        }
        Spacer(modifier = Modifier.height(8.dp.uiScaled()))
        if (copied) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp.uiScaled())
                )
                Spacer(modifier = Modifier.width(4.dp.uiScaled()))
                Text(
                    text = "Copied to clipboard",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp.uiScaled()))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp.uiScaled())
        ) {
            @Suppress("DEPRECATION")
            val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
            OutlinedButton(
                onClick = {
                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(key))
                    copied = true
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp.uiScaled()))
                Spacer(modifier = Modifier.width(4.dp.uiScaled()))
                Text(stringResource(R.string.ssh_key_copy))
            }
            OutlinedButton(
                onClick = { showRotateConfirm = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp.uiScaled()))
                Spacer(modifier = Modifier.width(4.dp.uiScaled()))
                Text(stringResource(R.string.ssh_key_rotate))
            }
        }
    } ?: run {
        Text(
            text = "Generating key pair…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }

    if (showRotateConfirm) {
        AlertDialog(
            onDismissRequest = { showRotateConfirm = false },
            title = { Text(stringResource(R.string.ssh_key_rotate_title)) },
            text = {
                Text(
                    "This permanently replaces the device SSH key pair. " +
                        "You will need to install the new public key on every server. Continue?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    publicKey = viewModel.rotateSshKey()
                    copied = false
                    showRotateConfirm = false
                }) { Text(stringResource(R.string.ssh_key_rotate_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showRotateConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

// ---- helpers ----

private fun transportIcon(transport: HostTransport) = when (transport) {
    HostTransport.DIRECT -> Icons.Default.Dns
    HostTransport.SSH_TUNNEL -> Icons.Default.VpnKey
}

@Composable
private fun transportLabel(transport: HostTransport) = when (transport) {
    HostTransport.DIRECT -> stringResource(R.string.host_profiles_transport_direct)
    HostTransport.SSH_TUNNEL -> stringResource(R.string.host_profiles_transport_ssh)
}

private fun defaultServerUrl(transport: HostTransport) = when (transport) {
    HostTransport.DIRECT -> "http://localhost:4096"
    HostTransport.SSH_TUNNEL -> "http://127.0.0.1:4096"
}
