package com.yage.opencode_client.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yage.opencode_client.R
import com.yage.opencode_client.data.model.FileNode
import com.yage.opencode_client.ui.AIBuilderSettings
import com.yage.opencode_client.ui.AppState
import com.yage.opencode_client.ui.theme.uiScaled
import com.yage.opencode_client.util.LanguageMode
import com.yage.opencode_client.util.ThemeMode
import kotlin.math.roundToInt

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

    Spacer(modifier = Modifier.height(12.dp.uiScaled()))

    OutlinedTextField(
        value = username,
        onValueChange = onUsernameChange,
        label = { Text(stringResource(R.string.username_optional)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
    )

    Spacer(modifier = Modifier.height(12.dp.uiScaled()))

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

    Spacer(modifier = Modifier.height(12.dp.uiScaled()))

    OutlinedButton(
        onClick = { showDirectoryPicker = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.FolderOpen, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp.uiScaled()))
        Text(stringResource(R.string.browse_server_directory))
    }

    Spacer(modifier = Modifier.height(12.dp.uiScaled()))

    if (recentWorkingDirectories.isNotEmpty()) {
        var showRecentDialog by remember { mutableStateOf(false) }

        OutlinedButton(
            onClick = { showRecentDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp.uiScaled()))
            Text(stringResource(R.string.recent_working_directories))
        }
        Spacer(modifier = Modifier.height(12.dp.uiScaled()))

        if (showRecentDialog) {
            AlertDialog(
                onDismissRequest = { showRecentDialog = false },
                title = { Text(stringResource(R.string.recent_working_directories)) },
                text = {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(recentWorkingDirectories) { recentDirectory ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = recentDirectory,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                modifier = Modifier.clickable {
                                    onSelectRecentWorkingDirectory(recentDirectory)
                                    showRecentDialog = false
                                }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showRecentDialog = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }
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

    Spacer(modifier = Modifier.height(16.dp.uiScaled()))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp.uiScaled())
    ) {
        Button(
            onClick = onTestConnection,
            enabled = serverUrl.isNotBlank() && !isTesting
        ) {
            if (isTesting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp.uiScaled()),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp.uiScaled()))
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
        Spacer(modifier = Modifier.height(12.dp.uiScaled()))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp.uiScaled())
            )
            Spacer(modifier = Modifier.width(8.dp.uiScaled()))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerDirectoryPickerDialog(
    initialDirectory: String?,
    onDismiss: () -> Unit,
    onSelectDirectory: (String) -> Unit,
    loadDirectories: suspend (String?) -> Result<List<FileNode>>
) {
    var currentDirectory by remember(initialDirectory) { mutableStateOf(initialDirectory) }
    var confirmedDirectory by remember(initialDirectory) { mutableStateOf(initialDirectory) }
    var directories by remember { mutableStateOf<List<FileNode>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Merged input: auto-detect path jump vs filter
    var searchText by remember { mutableStateOf("") }
    var pathError by remember { mutableStateOf<String?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    val pathNotFoundText = stringResource(R.string.path_not_found)

    // Detect: starts with "/" or "\" or "X:" → path jump, else → filter
    val isPathInput = searchText.trim().let { t ->
        t.startsWith("/") || t.startsWith("\\") || Regex("^[A-Za-z]:").containsMatchIn(t)
    }

    LaunchedEffect(currentDirectory) {
        // Capture requested path to guard against stale results from cancelled loads
        val requestedDirectory = currentDirectory
        isLoading = true
        errorMessage = null
        pathError = null
        // Only clear search when navigating successfully (not on failed jumps)
        if (requestedDirectory == confirmedDirectory) {
            searchText = ""
        }
        loadDirectories(requestedDirectory)
            .onSuccess { nodes ->
                // Only apply if this is still the current request
                if (currentDirectory == requestedDirectory) {
                    directories = nodes.filter { it.isDirectory }.sortedBy { it.name.lowercase() }
                    if (requestedDirectory == null) {
                        inferParentDirectoryFromNodes(nodes)?.let { inferred ->
                            currentDirectory = inferred
                        }
                    }
                    confirmedDirectory = requestedDirectory
                    searchText = ""
                }
            }
            .onFailure { error ->
                if (currentDirectory == requestedDirectory) {
                    directories = emptyList()
                    val msg = error.message ?: error.toString()
                    if (msg.contains("not found", ignoreCase = true) ||
                        msg.contains("404", ignoreCase = true) ||
                        msg.contains("does not exist", ignoreCase = true)
                    ) {
                        pathError = pathNotFoundText
                    }
                    errorMessage = msg
                }
            }
        if (currentDirectory == requestedDirectory) {
            isLoading = false
        }
    }

    val parentDirectory = confirmedDirectory?.let(::parentServerDirectory)

    val breadcrumbSegments = remember(confirmedDirectory) {
        val dir = confirmedDirectory
        if (dir == null) emptyList()
        else buildBreadcrumbSegments(dir)
    }

    // Filter only when search is NOT a path
    val filteredDirectories = remember(directories, searchText, isPathInput) {
        if (searchText.isBlank() || isPathInput) directories
        else directories.filter { it.name.contains(searchText, ignoreCase = true) }
    }

    // Confirm disabled while loading or when there's any error (path or general)
    val canConfirm = !isLoading && pathError == null && errorMessage == null

    val executePathJump: () -> Unit = {
        val target = searchText.trim()
        if (target.isNotBlank() && isPathInput) {
            pathError = null
            currentDirectory = target
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.select_working_directory)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSearch = !showSearch }) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = stringResource(R.string.filter_directories_hint)
                            )
                        }
                        IconButton(
                            onClick = { onSelectDirectory(confirmedDirectory.orEmpty()) },
                            enabled = canConfirm
                        ) {
                            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.use_current_directory))
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Breadcrumb bar (sticky)
                if (breadcrumbSegments.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp.uiScaled(), vertical = 6.dp.uiScaled()),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            breadcrumbSegments.forEachIndexed { index, segment ->
                                if (index > 0) {
                                    Text(
                                        text = " / ",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                val isLast = index == breadcrumbSegments.lastIndex
                                TextButton(
                                    onClick = { currentDirectory = segment.path },
                                    enabled = !isLast,
                                    contentPadding = PaddingValues(
                                        horizontal = 4.dp.uiScaled(),
                                        vertical = 2.dp.uiScaled()
                                    )
                                ) {
                                    Text(
                                        text = segment.label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isLast) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Search / path input (toggle via search icon)
                AnimatedVisibility(visible = showSearch) {
                    Column {
                        OutlinedTextField(
                            value = searchText,
                            onValueChange = { searchText = it; pathError = null },
                            placeholder = {
                                Text(
                                    if (isPathInput) stringResource(R.string.path_input_hint)
                                    else stringResource(R.string.filter_directories_hint)
                                )
                            },
                            singleLine = true,
                            isError = pathError != null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp.uiScaled())
                                .focusRequester(searchFocusRequester),
                            supportingText = pathError?.let {
                                { Text(it, color = MaterialTheme.colorScheme.error) }
                            },
                            leadingIcon = {
                                Icon(
                                    if (isPathInput) Icons.Default.FolderOpen else Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp.uiScaled())
                                )
                            },
                            trailingIcon = {
                                if (isPathInput) {
                                    IconButton(
                                        onClick = executePathJump,
                                        enabled = searchText.isNotBlank() && !isLoading
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = stringResource(R.string.jump_to_path)
                                        )
                                    }
                                }
                            },
                            keyboardOptions = if (isPathInput) KeyboardOptions(imeAction = ImeAction.Go)
                            else KeyboardOptions.Default,
                            keyboardActions = if (isPathInput) KeyboardActions(onGo = { executePathJump() })
                            else KeyboardActions.Default
                        )
                    }
                }

                // Auto-focus search field when opened
                LaunchedEffect(showSearch) {
                    if (showSearch) {
                        searchFocusRequester.requestFocus()
                    }
                }

                // Directory list — fills remaining space
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when {
                        isLoading -> {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(24.dp.uiScaled())
                                    .align(Alignment.Center),
                                strokeWidth = 2.dp.uiScaled()
                            )
                        }
                        errorMessage != null && filteredDirectories.isEmpty() -> {
                            Text(
                                text = errorMessage ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(16.dp.uiScaled())
                            )
                        }
                        filteredDirectories.isEmpty() -> {
                            Text(
                                text = if (directories.isNotEmpty())
                                    stringResource(R.string.no_matching_directories)
                                else stringResource(R.string.no_subdirectories),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(16.dp.uiScaled())
                            )
                        }
                        else -> {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(filteredDirectories, key = { it.absolute ?: it.path }) { directory ->
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

                // Bottom action bar
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp.uiScaled(), vertical = 8.dp.uiScaled()),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { currentDirectory = parentDirectory },
                        enabled = parentDirectory != null && !isLoading
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp.uiScaled())
                        )
                        Spacer(modifier = Modifier.width(4.dp.uiScaled()))
                        Text(stringResource(R.string.parent_directory))
                    }
                    Button(
                        onClick = { onSelectDirectory(confirmedDirectory.orEmpty()) },
                        enabled = canConfirm
                    ) {
                        Text(
                            if (confirmedDirectory == null)
                                stringResource(R.string.use_server_default_directory)
                            else stringResource(R.string.use_current_directory)
                        )
                    }
                }
            }
        }
    }
}

/** Parse a path into breadcrumb segments with their accumulated paths. */
private data class BreadcrumbSegment(val label: String, val path: String)

private fun buildBreadcrumbSegments(path: String): List<BreadcrumbSegment> {
    val normalized = path.replace('\\', '/')
    val isWindowsDrive = Regex("^[A-Za-z]:/?$").matches(normalized)

    if (isWindowsDrive) {
        return listOf(BreadcrumbSegment(normalized.trimEnd('/'), normalized.trimEnd('/')))
    }

    val parts = normalized.trim('/').split('/').filter { it.isNotEmpty() }
    if (parts.isEmpty() && normalized.startsWith("/")) {
        return listOf(BreadcrumbSegment("/", "/"))
    }

    val segments = mutableListOf<BreadcrumbSegment>()
    // Add root for Unix paths
    if (normalized.startsWith("/")) {
        segments.add(BreadcrumbSegment("/", "/"))
    }
    var accumulated = if (normalized.startsWith("/")) "" else ""
    for ((index, part) in parts.withIndex()) {
        accumulated = if (normalized.startsWith("/")) {
            if (index == 0) "/$part" else "$accumulated/$part"
        } else {
            if (index == 0) part else "$accumulated/$part"
        }
        segments.add(BreadcrumbSegment(part, accumulated))
    }
    return segments
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
    languageMode: LanguageMode,
    fontSizeScale: Float,
    uiScale: Float,
    onThemeSelected: (ThemeMode) -> Unit,
    onLanguageSelected: (LanguageMode) -> Unit,
    onFontSizeScaleChanged: (Float) -> Unit,
    onFontSizeScaleChangeFinished: () -> Unit,
    onUiScaleChanged: (Float) -> Unit,
    onUiScaleChangeFinished: () -> Unit
) {
    SectionHeader(title = stringResource(R.string.appearance))

    ThemeMode.values().forEach { mode ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp.uiScaled()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = themeMode == mode,
                onClick = { onThemeSelected(mode) }
            )
            Spacer(modifier = Modifier.width(8.dp.uiScaled()))
            Text(
                when (mode) {
                    ThemeMode.LIGHT -> stringResource(R.string.light)
                    ThemeMode.DARK -> stringResource(R.string.dark)
                    ThemeMode.SYSTEM -> stringResource(R.string.system_default)
                }
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp.uiScaled()))

    Text(
        text = stringResource(R.string.language),
        style = MaterialTheme.typography.bodyMedium
    )
    LanguageMode.values().forEach { mode ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp.uiScaled()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = languageMode == mode,
                onClick = { onLanguageSelected(mode) }
            )
            Spacer(modifier = Modifier.width(8.dp.uiScaled()))
            Text(
                when (mode) {
                    LanguageMode.SYSTEM -> stringResource(R.string.system_default)
                    LanguageMode.ENGLISH -> stringResource(R.string.english)
                    LanguageMode.CHINESE -> stringResource(R.string.chinese)
                }
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp.uiScaled()))

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
            text = stringResource(R.string.scale_percentage, (fontSizeScale * 100).roundToInt()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }

    Slider(
        value = fontSizeScale,
        onValueChange = onFontSizeScaleChanged,
        onValueChangeFinished = onFontSizeScaleChangeFinished,
        valueRange = 0.7f..1.6f,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(12.dp.uiScaled()))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.interface_scale),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = stringResource(R.string.scale_percentage, (uiScale * 100).roundToInt()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }

    Slider(
        value = uiScale,
        onValueChange = onUiScaleChanged,
        onValueChangeFinished = onUiScaleChangeFinished,
        valueRange = 0.65f..1.35f,
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
    hideMicIcon: Boolean,
    onBaseUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onPromptChange: (String) -> Unit,
    onTerminologyChange: (String) -> Unit,
    onToggleTokenVisibility: () -> Unit,
    onHideMicIconChange: (Boolean) -> Unit,
    onTestConnection: () -> Unit,
    onSave: () -> Unit
) {
    SectionHeader(title = stringResource(R.string.speech_recognition_settings))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.hide_mic_icon),
            style = MaterialTheme.typography.bodyMedium
        )
        Switch(
            checked = hideMicIcon,
            onCheckedChange = onHideMicIconChange
        )
    }

    Spacer(modifier = Modifier.height(12.dp.uiScaled()))

    OutlinedTextField(
        value = aiBuilderBaseURL,
        onValueChange = onBaseUrlChange,
        label = { Text(stringResource(R.string.ai_builder_base_url)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Cloud, contentDescription = null) }
    )

    Spacer(modifier = Modifier.height(12.dp.uiScaled()))

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

    Spacer(modifier = Modifier.height(12.dp.uiScaled()))

    OutlinedTextField(
        value = aiBuilderCustomPrompt,
        onValueChange = onPromptChange,
        label = { Text(stringResource(R.string.custom_prompt)) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        maxLines = 6
    )

    Spacer(modifier = Modifier.height(12.dp.uiScaled()))

    OutlinedTextField(
        value = aiBuilderTerminology,
        onValueChange = onTerminologyChange,
        label = { Text(stringResource(R.string.terminology)) },
        placeholder = { Text(stringResource(R.string.terminology_hint)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    Spacer(modifier = Modifier.height(16.dp.uiScaled()))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp.uiScaled())
    ) {
        Button(
            onClick = onTestConnection,
            enabled = aiBuilderBaseURL.isNotBlank() && !state.isTestingAIBuilderConnection
        ) {
            if (state.isTestingAIBuilderConnection) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp.uiScaled()),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp.uiScaled()))
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

    Spacer(modifier = Modifier.height(8.dp.uiScaled()))

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
    Spacer(modifier = Modifier.height(16.dp.uiScaled()))
}

@Composable
private fun ResultCard(result: TestResult) {
    Spacer(modifier = Modifier.height(12.dp.uiScaled()))
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
                .padding(12.dp.uiScaled()),
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
            Spacer(modifier = Modifier.width(8.dp.uiScaled()))
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
    Spacer(modifier = Modifier.height(32.dp.uiScaled()))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(32.dp.uiScaled()))
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
