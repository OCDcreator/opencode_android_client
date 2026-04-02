package com.yage.opencode_client.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yage.opencode_client.R
import com.yage.opencode_client.data.model.FileNode
import com.yage.opencode_client.ui.AIBuilderSettings
import com.yage.opencode_client.ui.AppState
import com.yage.opencode_client.util.ThemeMode

@Composable
internal fun ServerConnectionSection(
    serverUrl: String,
    username: String,
    password: String,
    workingDirectory: String,
    showPassword: Boolean,
    isTesting: Boolean,
    state: AppState,
    testResult: TestResult?,
    onServerUrlChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onWorkingDirectoryChange: (String) -> Unit,
    recentWorkingDirectories: List<String>,
    onSelectRecentWorkingDirectory: (String) -> Unit,
    onBrowseWorkingDirectory: suspend (String?) -> Result<List<FileNode>>,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onTestConnection: () -> Unit,
    onSave: () -> Unit
) {
    var showDirectoryPicker by remember { mutableStateOf(false) }

    SectionHeader(title = stringResource(R.string.server_connection))

    OutlinedTextField(
        value = serverUrl,
        onValueChange = onServerUrlChange,
        label = { Text(stringResource(R.string.server_url)) },
        placeholder = { Text(stringResource(R.string.server_url_hint)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Cloud, contentDescription = null) }
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = username,
        onValueChange = onUsernameChange,
        label = { Text(stringResource(R.string.username_optional)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = workingDirectory,
        onValueChange = onWorkingDirectoryChange,
        label = { Text(stringResource(R.string.working_directory)) },
        placeholder = { Text(stringResource(R.string.working_directory_hint)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = { showDirectoryPicker = true }) {
                Icon(
                    Icons.Default.FolderOpen,
                    contentDescription = stringResource(R.string.browse_server_directory)
                )
            }
        }
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedButton(
        onClick = { showDirectoryPicker = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.FolderOpen, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.browse_server_directory))
    }

    Spacer(modifier = Modifier.height(12.dp))

    if (recentWorkingDirectories.isNotEmpty()) {
        Text(
            text = stringResource(R.string.recent_working_directories),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            recentWorkingDirectories.forEach { recentDirectory ->
                AssistChip(
                    onClick = { onSelectRecentWorkingDirectory(recentDirectory) },
                    label = {
                        Text(
                            text = recentDirectory,
                            maxLines = 1
                        )
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }

    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text(stringResource(R.string.password_optional)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onTogglePasswordVisibility) {
                Icon(
                    if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (showPassword) stringResource(R.string.hide_password_cd) else stringResource(R.string.show_password_cd)
                )
            }
        },
        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }
    )

    Spacer(modifier = Modifier.height(16.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onTestConnection,
            enabled = serverUrl.isNotBlank() && !isTesting
        ) {
            if (isTesting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(stringResource(R.string.test_connection))
        }

        OutlinedButton(
            onClick = onSave,
            enabled = serverUrl.isNotBlank()
        ) {
            Text(stringResource(R.string.save))
        }
    }

    testResult?.let { ResultCard(result = it) }

    if (state.isConnected) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                stringResource(R.string.connected),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            state.serverVersion?.let { version ->
                Text(
                    " (v$version)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }

    if (showDirectoryPicker) {
        ServerDirectoryPickerDialog(
            initialDirectory = workingDirectory.ifBlank { null },
            onDismiss = { showDirectoryPicker = false },
            onSelectDirectory = {
                onWorkingDirectoryChange(it)
                showDirectoryPicker = false
            },
            loadDirectories = onBrowseWorkingDirectory
        )
    }
}

@Composable
private fun ServerDirectoryPickerDialog(
    initialDirectory: String?,
    onDismiss: () -> Unit,
    onSelectDirectory: (String) -> Unit,
    loadDirectories: suspend (String?) -> Result<List<FileNode>>
) {
    var currentDirectory by remember(initialDirectory) { mutableStateOf(initialDirectory) }
    var directories by remember { mutableStateOf<List<FileNode>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentDirectory) {
        isLoading = true
        errorMessage = null
        loadDirectories(currentDirectory)
            .onSuccess { nodes ->
                directories = nodes.filter { it.isDirectory }.sortedBy { it.name.lowercase() }
                if (currentDirectory == null) {
                    inferParentDirectoryFromNodes(nodes)?.let { inferred ->
                        currentDirectory = inferred
                    }
                }
            }
            .onFailure { error ->
                directories = emptyList()
                errorMessage = error.message ?: error.toString()
            }
        isLoading = false
    }

    val parentDirectory = currentDirectory?.let(::parentServerDirectory)
    val supportsUnixShortcuts = currentDirectory?.startsWith("/") == true ||
        directories.any { it.absolute?.startsWith("/") == true }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_working_directory)) },
        text = {
            Column {
                Text(
                    text = currentDirectory ?: stringResource(R.string.server_default_directory),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (supportsUnixShortcuts) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = { currentDirectory = "/" },
                            label = { Text(stringResource(R.string.go_to_root)) }
                        )
                        AssistChip(
                            onClick = { currentDirectory = "/Volumes" },
                            label = { Text(stringResource(R.string.go_to_volumes)) }
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { currentDirectory = parentDirectory },
                        enabled = parentDirectory != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.parent_directory))
                    }
                    Button(
                        onClick = { onSelectDirectory(currentDirectory.orEmpty()) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            if (currentDirectory == null) {
                                stringResource(R.string.use_server_default_directory)
                            } else {
                                stringResource(R.string.use_current_directory)
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                when {
                    isLoading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                    errorMessage != null -> {
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    directories.isEmpty() -> {
                        Text(
                            text = stringResource(R.string.no_subdirectories),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                        ) {
                            items(directories, key = { it.path }) { directory ->
                                ListItem(
                                    headlineContent = { Text(directory.name) },
                                    supportingContent = {
                                        directory.absolute?.let { Text(it) }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            currentDirectory = directory.absolute ?: directory.path
                                        }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun parentServerDirectory(path: String): String? {
    val separator = if (path.contains('\\')) '\\' else '/'
    val normalized = path.replace('\\', '/').trimEnd('/')
    if (normalized.isEmpty() || normalized == "/") return null
    if (Regex("^[A-Za-z]:$").matches(normalized)) return null
    if (normalized.startsWith("//")) {
        val parts = normalized.split('/').filter { it.isNotEmpty() }
        if (parts.size <= 2) return null
    }
    val index = normalized.lastIndexOf('/')
    if (index < 0) return null
    val parent = when {
        index == 0 -> "/"
        index == 2 && normalized.getOrNull(1) == ':' -> normalized.substring(0, 2)
        else -> normalized.substring(0, index)
    }
    return if (separator == '\\') parent.replace('/', '\\') else parent
}

private fun inferParentDirectoryFromNodes(nodes: List<FileNode>): String? {
    val absolutes = nodes.mapNotNull { it.absolute?.replace('\\', '/') }.filter { it.isNotBlank() }
    if (absolutes.isEmpty()) return null
    val parents = absolutes.map { absolute ->
        val trimmed = absolute.trimEnd('/')
        val index = trimmed.lastIndexOf('/')
        when {
            index < 0 -> null
            index == 0 -> "/"
            else -> trimmed.substring(0, index)
        }
    }.distinct()
    return parents.singleOrNull()
}

@Composable
internal fun AppearanceSection(
    themeMode: ThemeMode,
    fontSizeScale: Float,
    onThemeSelected: (ThemeMode) -> Unit,
    onFontSizeScaleChanged: (Float) -> Unit
) {
    SectionHeader(title = stringResource(R.string.appearance))

    ThemeMode.values().forEach { mode ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = themeMode == mode,
                onClick = { onThemeSelected(mode) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                when (mode) {
                    ThemeMode.LIGHT -> stringResource(R.string.light)
                    ThemeMode.DARK -> stringResource(R.string.dark)
                    ThemeMode.SYSTEM -> stringResource(R.string.system_default)
                }
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.font_size),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = when {
                fontSizeScale < 0.95f -> stringResource(R.string.font_size_small)
                fontSizeScale > 1.05f -> stringResource(R.string.font_size_large)
                else -> stringResource(R.string.font_size_default)
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }

    Slider(
        value = fontSizeScale,
        onValueChange = onFontSizeScaleChanged,
        valueRange = 0.8f..1.4f,
        steps = 2,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
internal fun SpeechRecognitionSection(
    state: AppState,
    aiBuilderBaseURL: String,
    aiBuilderToken: String,
    aiBuilderCustomPrompt: String,
    aiBuilderTerminology: String,
    showAIBuilderToken: Boolean,
    onBaseUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onPromptChange: (String) -> Unit,
    onTerminologyChange: (String) -> Unit,
    onToggleTokenVisibility: () -> Unit,
    onTestConnection: () -> Unit,
    onSave: () -> Unit
) {
    SectionHeader(title = stringResource(R.string.speech_recognition_settings))

    OutlinedTextField(
        value = aiBuilderBaseURL,
        onValueChange = onBaseUrlChange,
        label = { Text(stringResource(R.string.ai_builder_base_url)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Cloud, contentDescription = null) }
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = aiBuilderToken,
        onValueChange = onTokenChange,
        label = { Text(stringResource(R.string.ai_builder_token)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (showAIBuilderToken) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onToggleTokenVisibility) {
                Icon(
                    if (showAIBuilderToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (showAIBuilderToken) stringResource(R.string.hide_token_cd) else stringResource(R.string.show_token_cd)
                )
            }
        },
        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = aiBuilderCustomPrompt,
        onValueChange = onPromptChange,
        label = { Text(stringResource(R.string.custom_prompt)) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        maxLines = 6
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = aiBuilderTerminology,
        onValueChange = onTerminologyChange,
        label = { Text(stringResource(R.string.terminology)) },
        placeholder = { Text(stringResource(R.string.terminology_hint)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    Spacer(modifier = Modifier.height(16.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onTestConnection,
            enabled = aiBuilderBaseURL.isNotBlank() && !state.isTestingAIBuilderConnection
        ) {
            if (state.isTestingAIBuilderConnection) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(stringResource(R.string.test_connection))
        }

        OutlinedButton(
            onClick = onSave,
            enabled = aiBuilderBaseURL.isNotBlank()
        ) {
            Text(stringResource(R.string.save))
        }
    }

    if (state.aiBuilderConnectionOK || state.aiBuilderConnectionError != null) {
        ResultCard(
            result = TestResult(
                success = state.aiBuilderConnectionOK,
                message = if (state.aiBuilderConnectionOK) {
                    stringResource(R.string.connected_successfully)
                } else {
                    state.aiBuilderConnectionError ?: stringResource(R.string.connection_failed)
                }
            )
        )
    }
}

@Composable
internal fun AboutSection() {
    SectionHeader(title = stringResource(R.string.about))

    Text(
        stringResource(R.string.about_title),
        style = MaterialTheme.typography.bodyLarge
    )
    Text(
        stringResource(R.string.version_format, "1.0"),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        stringResource(R.string.about_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium
    )
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun ResultCard(result: TestResult) {
    Spacer(modifier = Modifier.height(12.dp))
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (result.success) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (result.success) Icons.Default.Check else Icons.Default.Error,
                contentDescription = null,
                tint = if (result.success) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                result.message,
                color = if (result.success) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
            )
        }
    }
}

@Composable
internal fun SettingsSectionDivider() {
    Spacer(modifier = Modifier.height(32.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(32.dp))
}

internal data class TestResult(
    val success: Boolean,
    val message: String
)

internal fun buildAIBuilderSettings(
    baseURL: String,
    token: String,
    customPrompt: String,
    terminology: String
): AIBuilderSettings {
    return AIBuilderSettings(
        baseURL = baseURL,
        token = token,
        customPrompt = customPrompt,
        terminology = terminology
    )
}
