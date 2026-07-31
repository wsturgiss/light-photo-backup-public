package com.stan.lightphotobackup.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.stan.lightphotobackup.BuildConfig
import com.stan.lightphotobackup.backup.BackupProvider
import com.stan.lightphotobackup.database.BackupStatus
import com.stan.lightphotobackup.settings.PERIODIC_FREQUENCY_MINUTES
import com.thelightphone.sdk.ui.*

enum class PhotoAccess { NONE, LIMITED, FULL }

@Composable
fun PhotoBackupTheme(content: @Composable () -> Unit) {
    val colors by LightThemeController.colors.collectAsState()
    LightTheme(colors = colors, content = content)
}

@Composable
private fun ScreenFrame(
    title: String? = null,
    leftButton: LightTopBarButton? = null,
    bottomItems: List<LightBottomBarItem?> = emptyList(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(LightThemeTokens.colors.background),
    ) {
        if (title != null) {
            LightTopBar(
                leftButton = leftButton,
                center = LightTopBarCenter.Text(title),
            )
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 1.25f.gridUnitsAsDp()),
            content = content,
        )
        if (bottomItems.isNotEmpty()) LightBottomBar(bottomItems)
    }
}

@Composable
private fun SectionLabel(text: String) {
    LightText(text, LightTextVariant.Detail)
    Spacer(Modifier.height(.12f.gridUnitsAsDp()))
}

@Composable
private fun Value(text: String, monospace: Boolean = false) {
    LightText(text, LightTextVariant.Heading, monospace = monospace)
}

@Composable
private fun InformationRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.lightClickable(onClickLabel = "$label: $value", onClick = onClick)
                else Modifier,
            )
            .padding(vertical = .85f.gridUnitsAsDp())
            .semantics { contentDescription = "$label, $value" },
    ) {
        SectionLabel(label)
        Value(value)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .lightClickable(onClickLabel = "$label, ${if (checked) "on" else "off"}", onClick = onClick)
            .padding(vertical = .85f.gridUnitsAsDp())
            .semantics { contentDescription = "$label, ${if (checked) "on" else "off"}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightIcon(
            // The Light SDK names these assets from the inverse-theme convention.
            // Display the knob in the familiar left=off, right=on direction here.
            icon = if (checked) LightIcons.TOGGLE_OFF else LightIcons.TOGGLE_ON,
            size = 1.5f,
            contentDescription = null,
        )
        Spacer(Modifier.width(.75f.gridUnitsAsDp()))
        LightText(label, LightTextVariant.Heading)
    }
}

private fun frequencyLabel(minutes: Int) = when (minutes) {
    15 -> "Every 15 minutes"
    30 -> "Every 30 minutes"
    60 -> "Every hour"
    360 -> "Every 6 hours"
    1440 -> "Daily"
    else -> "Every 30 minutes"
}

@Composable
private fun ProviderRow(provider: BackupProvider, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.lightClickable(onClickLabel = "${provider.displayName}, ${if (selected) "selected" else "not selected"}", onClick = onClick) else Modifier)
            .padding(start = 2.25f.gridUnitsAsDp(), top = .4f.gridUnitsAsDp(), bottom = .4f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightIcon(if (selected) LightIcons.SELECT_ON else LightIcons.SELECT_OFF, size = .7f, contentDescription = null)
        Spacer(Modifier.width(.55f.gridUnitsAsDp()))
        LightText(provider.displayName, LightTextVariant.Copy)
    }
}

@Composable
private fun ProviderSection(state: BackupUiState, vm: BackupViewModel) {
    SectionLabel("Image backup provider")
    BackupProvider.available.forEach { provider ->
        ProviderRow(provider, state.provider == provider, true) { vm.provider(provider) }
    }
}

@Composable
private fun FrequencyRow(minutes: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .lightClickable(
                onClickLabel = "${frequencyLabel(minutes)}, ${if (selected) "selected" else "not selected"}",
                onClick = onClick,
            )
            .padding(start = 2.25f.gridUnitsAsDp(), top = .4f.gridUnitsAsDp(), bottom = .4f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightIcon(
            icon = if (selected) LightIcons.SELECT_ON else LightIcons.SELECT_OFF,
            size = .7f,
            contentDescription = null,
        )
        Spacer(Modifier.width(.55f.gridUnitsAsDp()))
        LightText(frequencyLabel(minutes), LightTextVariant.Copy)
    }
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    LightText(
        label,
        LightTextVariant.Heading,
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClickLabel = label, onClick = onClick)
            .padding(vertical = .85f.gridUnitsAsDp()),
    )
}

@Composable
private fun BodyMessage(title: String, message: String? = null) {
    Spacer(Modifier.height(1f.gridUnitsAsDp()))
    LightText(title, LightTextVariant.Heading)
    if (message != null) {
        Spacer(Modifier.height(.75f.gridUnitsAsDp()))
        LightText(message, LightTextVariant.Paragraph, lighten = true)
    }
}

private fun waitingCount(state: BackupUiState) = state.counts
    .filterKeys {
        it in setOf(
            BackupStatus.DISCOVERED,
            BackupStatus.PENDING,
            BackupStatus.RETRYABLE_FAILURE,
            BackupStatus.AWAITING_MEDIA_CREATION,
        )
    }
    .values.sum()

private fun failedCount(state: BackupUiState) = (state.counts[BackupStatus.RETRYABLE_FAILURE] ?: 0) + (state.counts[BackupStatus.PERMANENT_FAILURE] ?: 0)

private fun statusText(state: BackupUiState, waiting: Int) = when (state.manualStatus) {
    ManualStatus.STARTING -> "Starting backup"
    ManualStatus.WAITING_FOR_NETWORK -> "Waiting for network"
    ManualStatus.RUNNING -> "Backing up"
    ManualStatus.SUCCEEDED -> if (state.summary.uploaded > 0) "${state.summary.uploaded} photos backed up" else "Backup complete"
    ManualStatus.FAILED -> "Backup failed"
    ManualStatus.AUTHORIZATION_EXPIRED -> "Account connection expired"
    ManualStatus.IDLE -> when {
        failedCount(state) > 0 -> if (state.provider == BackupProvider.IMMICH) state.summary.lastError ?: "Backup needs attention" else "Backup needs attention"
        waiting == 0 -> "Up to date"
        else -> "Photos waiting"
    }
}

@Composable
fun BackupScreen(
    state: BackupUiState,
    access: PhotoAccess,
    requestPermission: () -> Unit,
    details: () -> Unit,
    configureImmich: () -> Unit,
    vm: BackupViewModel,
) {
    val waiting = waitingCount(state)
    val bottomAction = when {
        access == PhotoAccess.LIMITED -> LightBarButton.Text("Allow all photo access", onClick = requestPermission)
        access == PhotoAccess.NONE -> LightBarButton.Text("Allow photo access", onClick = requestPermission)
        state.provider == BackupProvider.GOOGLE_PHOTOS && !state.configured -> null
        state.pairing != null -> LightBarButton.Text("Cancel", onClick = vm::cancelPairing)
        !state.connected -> LightBarButton.Text(if (state.provider == BackupProvider.IMMICH) "Configure Immich" else "Connect account", onClick = if (state.provider == BackupProvider.IMMICH) configureImmich else vm::pair)
        state.running -> LightBarButton.Text("Stop backup", onClick = vm::stop)
        else -> null
    }

    ScreenFrame(
        bottomItems = listOfNotNull(bottomAction),
    ) {
        when {
            access == PhotoAccess.LIMITED -> BodyMessage(
                "Limited photo access",
                "Automatic backup requires access to all photos in the Light and Screenshots folders.",
            )

            access == PhotoAccess.NONE -> BodyMessage(
                "Photo access required",
                "Photo Backup needs full permission to read camera photos so it can upload them.",
            )

            state.provider == BackupProvider.GOOGLE_PHOTOS && !state.configured -> BodyMessage(
                "Server not configured",
                "Set PHOTO_BACKUP_AUTH_SERVER_URL and rebuild the app.",
            )

            state.provider == BackupProvider.GOOGLE_PHOTOS && state.pairing != null -> {
                ProviderSection(state, vm)
                BodyMessage("Connect Google account", "On another device, open:")
                Spacer(Modifier.height(.6f.gridUnitsAsDp()))
                SelectionContainer {
                    LightText(state.pairing.verificationUrl, LightTextVariant.Paragraph, underline = true)
                }
                Spacer(Modifier.height(1.25f.gridUnitsAsDp()))
                SectionLabel("Enter code")
                SelectionContainer {
                    LightText(state.pairing.pairingCode, LightTextVariant.Heading, monospace = true)
                }
                Spacer(Modifier.height(1f.gridUnitsAsDp()))
                LightText("Waiting for connection", LightTextVariant.Detail, lighten = true)
            }

            !state.connected -> {
                ProviderSection(state, vm)
                BodyMessage(
                    if (state.provider == BackupProvider.IMMICH) "Immich not connected" else "Not connected",
                    state.pairingError ?: if (state.provider == BackupProvider.IMMICH) "Configure your Immich server and API key to begin automatic backup." else "Connect your Google account to begin automatic backup.",
                )
            }

            else -> {
                InformationRow("Provider", state.provider.displayName)
                InformationRow("Status", statusText(state, waiting))
                InformationRow("Photos waiting", waiting.toString())
                if (state.manualStatus != ManualStatus.RUNNING) {
                    ActionRow("Back up now", vm::backUpNow)
                }
                if (state.provider == BackupProvider.IMMICH && !state.running) {
                    ActionRow("Re-queue all photos", vm::requeueImmich)
                    LightText("Requeues every local photo. Immich detects duplicates.", LightTextVariant.Detail, lighten = true)
                }
                if (failedCount(state) > 0) {
                    ActionRow("Retry failed", vm::retry)
                }
                Spacer(Modifier.height(.8f.gridUnitsAsDp()))
                ToggleRow("Back up over cellular", state.cellular) {
                    vm.cellular(!state.cellular)
                }
                if (!state.cellular && state.manualStatus != ManualStatus.RUNNING) {
                    ActionRow("Back up over cellular now") { vm.backUpNow(true) }
                }
                ToggleRow("Periodic backup", state.periodic) {
                    vm.periodic(!state.periodic)
                }
                if (state.periodic) {
                    PERIODIC_FREQUENCY_MINUTES.forEach { minutes ->
                        FrequencyRow(minutes, state.periodicMinutes == minutes) {
                            vm.periodicMinutes(minutes)
                        }
                    }
                }
                ActionRow("Last backup details", details)
                Spacer(Modifier.height(.8f.gridUnitsAsDp()))
                InformationRow("Last backup", state.summary.lastSuccessMillis?.let(::formatDate) ?: "Not yet")
                InformationRow(
                    "Account",
                    if (state.manualStatus == ManualStatus.AUTHORIZATION_EXPIRED) "Reconnect" else "Connected",
                )
                ActionRow("Disconnect account", vm::disconnect)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun ImmichSetupScreen(state: BackupUiState, back: () -> Unit, vm: BackupViewModel) {
    var serverUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var allowHttp by remember { mutableStateOf(false) }
    val errorRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(state.pairingError) {
        if (state.pairingError != null) errorRequester.bringIntoView()
    }
    ScreenFrame(
        title = "Connect Immich",
        leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = back),
        bottomItems = listOf(LightBarButton.Text("Connect", onClick = { vm.configureImmich(serverUrl, apiKey, allowHttp) })),
    ) {
        BodyMessage("Immich server", "Enter your Immich server URL and an API key from its API Keys settings.")
        Spacer(Modifier.height(.8f.gridUnitsAsDp()))
        OutlinedTextField(value = serverUrl, onValueChange = { serverUrl = it }, label = { LightText("Server URL", LightTextVariant.Detail) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(.6f.gridUnitsAsDp()))
        OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { LightText("API key", LightTextVariant.Detail) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(.8f.gridUnitsAsDp()))
        ToggleRow("Allow unencrypted HTTP", allowHttp) { allowHttp = !allowHttp }
        if (allowHttp) LightText("HTTP sends your API key and photos without encryption. Use it only on a trusted local network.", LightTextVariant.Detail, lighten = true)
        state.pairingError?.let { error ->
            Spacer(Modifier.height(.8f.gridUnitsAsDp()))
            LightText(error, LightTextVariant.Detail, lighten = true, modifier = Modifier.bringIntoViewRequester(errorRequester))
        }
    }
}

fun formatDate(value: Long): String = java.text.DateFormat
    .getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
    .format(java.util.Date(value))

@Composable
fun DetailsScreen(state: BackupUiState, back: () -> Unit, vm: BackupViewModel) {
    ScreenFrame(
        title = "Backup Details",
        leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = back),
        bottomItems = listOf(LightBarButton.Text("Retry failed", onClick = vm::retry)),
    ) {
        InformationRow("Waiting", (state.counts[BackupStatus.PENDING] ?: 0).toString())
        InformationRow(
            "Failed",
            ((state.counts[BackupStatus.RETRYABLE_FAILURE] ?: 0) +
                (state.counts[BackupStatus.PERMANENT_FAILURE] ?: 0)).toString(),
        )
        InformationRow("Backed up", (state.counts[BackupStatus.UPLOADED] ?: 0).toString())
        InformationRow("Last scan", state.summary.lastScanMillis?.let(::formatDate) ?: "Not yet")
        InformationRow("Last error", state.summary.lastError ?: "None")
        Spacer(Modifier.height(1f.gridUnitsAsDp()))
        SectionLabel("Diagnostics")
        LightText(
            "App ${BuildConfig.VERSION_NAME}\n" +
                "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n" +
                "Server ${runCatching { java.net.URI(BuildConfig.AUTH_SERVER_BASE_URL).host }.getOrNull() ?: "not configured"}\n" +
                "Path categories: ${state.summary.pathCategories.ifBlank { "none" }}",
            LightTextVariant.Detail,
            lighten = true,
        )
        Spacer(Modifier.height(1f.gridUnitsAsDp()))
    }
}
