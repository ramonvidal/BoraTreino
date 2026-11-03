package com.boratreino.feature.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boratreino.core.model.BackupSummary
import com.boratreino.core.model.RestTimerRules
import com.boratreino.core.notify.NotificationPermission
import com.boratreino.core.settings.AppLanguage
import com.boratreino.core.ui.ExerciseCredit
import com.boratreino.core.ui.Stepper
import com.boratreino.core.ui.shortLabel
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.launch
import com.boratreino.core.ui.R as CoreUiR

/** What the Settings screen can ask for. */
internal data class SettingsActions(
    val onBack: () -> Unit = {},
    val onStepGoal: (up: Boolean) -> Unit = {},
    val onRemindersEnabled: (Boolean) -> Unit = {},
    val onToggleWeekday: (DayOfWeek) -> Unit = {},
    val onOpenTimePicker: () -> Unit = {},
    val onDismissTimePicker: () -> Unit = {},
    val onSetReminderTime: (LocalTime) -> Unit = {},
    val onRestEnabled: (Boolean) -> Unit = {},
    val onRestSeconds: (Int) -> Unit = {},
    val onAllowNotifications: () -> Unit = {},
    val onExport: () -> Unit = {},
    val onImport: () -> Unit = {},
    val onConfirmImport: () -> Unit = {},
    val onDismissImport: () -> Unit = {},
    val onChooseLanguage: (AppLanguage) -> Unit = {},
    val onHealthChange: (Boolean) -> Unit = {},
    val onGetHealthConnect: () -> Unit = {},
)

@Composable
internal fun SettingsScreen(
    onBack: () -> Unit,
    updatesCard: (@Composable () -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val resolver = context.contentResolver
    val stored by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    val is24Hour = remember(context) { DateFormat.is24HourFormat(context) }
    val state = stored.copy(is24Hour = is24Hour)

    val savedText = stringResource(R.string.settings_backup_saved)
    val importedText = stringResource(R.string.settings_backup_imported)
    val invalidText = stringResource(R.string.settings_backup_invalid)
    val tooNewText = stringResource(R.string.settings_backup_too_new)
    val failedText = stringResource(CoreUiR.string.error_generic)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val text = when (event) {
                SettingsEvent.BackupSaved -> savedText
                SettingsEvent.BackupImported -> importedText
                SettingsEvent.InvalidBackup -> invalidText
                SettingsEvent.NewerBackup -> tooNewText
                SettingsEvent.Failed -> failedText
            }
            launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(text)
            }
        }
    }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> if (uri != null) viewModel.exportTo(resolver, uri) }
    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.readBackup(resolver, uri) }
    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.setNotificationsGranted(NotificationPermission.isGranted(context)) }

    val healthPermission = rememberLauncherForActivityResult(viewModel.healthPermissionContract()) { granted ->
        viewModel.onHealthPermissionResult(granted)
    }

    // The permission can change in the system settings while we are away.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshHealth()
        viewModel.setNotificationsGranted(NotificationPermission.isGranted(context))
    }

    SettingsContent(
        state = state,
        updatesCard = updatesCard,
        actions = SettingsActions(
            onBack = onBack,
            onStepGoal = viewModel::stepGoal,
            onRemindersEnabled = viewModel::setRemindersEnabled,
            onToggleWeekday = viewModel::toggleWeekday,
            onOpenTimePicker = viewModel::openTimePicker,
            onDismissTimePicker = viewModel::dismissTimePicker,
            onSetReminderTime = viewModel::setReminderTime,
            onRestEnabled = viewModel::setRestEnabled,
            onRestSeconds = viewModel::setRestSeconds,
            onAllowNotifications = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onExport = { createDocument.launch(viewModel.suggestedFileName()) },
            onImport = { openDocument.launch(arrayOf("*/*")) },
            onConfirmImport = viewModel::confirmImport,
            onDismissImport = viewModel::dismissImport,
            onChooseLanguage = viewModel::chooseLanguage,
            onHealthChange = { on ->
                if (on) healthPermission.launch(viewModel.healthPermissions) else viewModel.turnHealthOff()
            },
            onGetHealthConnect = {
                try {
                    context.startActivity(viewModel.healthInstallIntent())
                } catch (e: ActivityNotFoundException) {
                    // No store on this phone: nothing to open.
                }
            },
        ),
        snackbarHostState = snackbar,
    )
}

/** Drawn from its state alone, so screenshot tests can render it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    updatesCard: (@Composable () -> Unit)? = null,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(
                            painter = painterResource(CoreUiR.drawable.ic_back),
                            contentDescription = stringResource(CoreUiR.string.back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (!state.loaded) {
            Column(Modifier.fillMaxSize().padding(innerPadding)) {}
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { GoalSection(state, actions) }
                item { RemindersSection(state, actions) }
                item { RestTimerSection(state, actions) }
                if (state.health != HealthCardState.Hidden) item { HealthSection(state.health, actions) }
                if (state.showNotificationsSection) item { NotificationsSection(actions) }
                item { BackupSection(actions) }
                if (updatesCard != null) item { updatesCard() }
                item { LanguageSection(state, actions) }
                item { AboutSection(state) }
            }
        }
    }

    if (state.timePickerOpen) TimeDialog(state, actions)
    if (state.importConfirm != null) ImportDialog(state.importConfirm, actions)
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SettingsChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

@Composable
private fun GoalSection(state: SettingsUiState, actions: SettingsActions) {
    SectionCard(stringResource(R.string.settings_goal_title)) {
        Stepper(
            label = stringResource(R.string.settings_goal_label),
            value = state.weeklyGoal.toString(),
            onMinus = { actions.onStepGoal(false) },
            onPlus = { actions.onStepGoal(true) },
            onValueClick = {},
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemindersSection(state: SettingsUiState, actions: SettingsActions) {
    val reminders = state.reminders
    val timeFormat = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()) }
    // Monday first keeps the row stable; the order of the week is not a setting.
    val days = remember { DayOfWeek.entries }
    SectionCard(stringResource(R.string.settings_reminders_title)) {
        SwitchRow(stringResource(R.string.settings_reminders_switch), reminders.enabled, actions.onRemindersEnabled)
        if (reminders.enabled) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = actions.onOpenTimePicker),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.settings_reminders_time),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    reminders.time.format(timeFormat),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                stringResource(R.string.settings_reminders_days),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                days.forEach { day ->
                    SettingsChip(day.shortLabel(), day in reminders.weekdays) { actions.onToggleWeekday(day) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RestTimerSection(state: SettingsUiState, actions: SettingsActions) {
    SectionCard(stringResource(R.string.settings_rest_title)) {
        SwitchRow(stringResource(R.string.settings_rest_switch), state.restTimer.enabled, actions.onRestEnabled)
        if (state.restTimer.enabled) {
            Text(
                stringResource(R.string.settings_rest_default),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RestTimerRules.OPTIONS.forEach { seconds ->
                    SettingsChip(
                        label = stringResource(R.string.settings_rest_seconds, seconds),
                        selected = state.restTimer.defaultSeconds == seconds,
                    ) { actions.onRestSeconds(seconds) }
                }
            }
        }
        Hint(stringResource(R.string.settings_rest_hint))
    }
}

@Composable
private fun HealthSection(health: HealthCardState, actions: SettingsActions) {
    SectionCard(stringResource(R.string.settings_health_title)) {
        Hint(stringResource(R.string.settings_health_hint))
        when (health) {
            HealthCardState.Hidden -> Unit
            HealthCardState.NeedsInstall -> {
                Hint(stringResource(R.string.settings_health_missing))
                Button(
                    onClick = actions.onGetHealthConnect,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.settings_health_get)) }
            }
            HealthCardState.Off, HealthCardState.PermissionDenied, HealthCardState.On -> {
                SwitchRow(
                    stringResource(R.string.settings_health_switch),
                    health == HealthCardState.On,
                    actions.onHealthChange,
                )
                if (health == HealthCardState.PermissionDenied) {
                    Text(
                        stringResource(R.string.settings_health_denied),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationsSection(actions: SettingsActions) {
    SectionCard(stringResource(R.string.settings_notifications_title)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.settings_notifications_off),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = actions.onAllowNotifications, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.settings_notifications_allow))
            }
        }
    }
}

@Composable
private fun BackupSection(actions: SettingsActions) {
    SectionCard(stringResource(R.string.settings_backup_title)) {
        Hint(stringResource(R.string.settings_backup_hint))
        Button(
            onClick = actions.onExport,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.settings_backup_export)) }
        OutlinedButton(
            onClick = actions.onImport,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.settings_backup_import)) }
    }
}

@Composable
private fun AboutSection(state: SettingsUiState) {
    SectionCard(stringResource(R.string.settings_about_title)) {
        Text(stringResource(R.string.settings_about, state.installedVersion), style = MaterialTheme.typography.bodyLarge)
        ExerciseCredit()
    }
}

@Composable
private fun LanguageSection(state: SettingsUiState, actions: SettingsActions) {
    SectionCard(stringResource(R.string.settings_language_title)) {
        Column(Modifier.selectableGroup()) {
            AppLanguage.entries.forEach { option ->
                val selected = option == state.language
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .selectable(selected = selected, role = Role.RadioButton) { actions.onChooseLanguage(option) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Text(option.label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(state: SettingsUiState, actions: SettingsActions) {
    val time = state.reminders.time
    val picker = rememberTimePickerState(
        initialHour = time.hour,
        initialMinute = time.minute,
        is24Hour = state.is24Hour,
    )
    AlertDialog(
        onDismissRequest = actions.onDismissTimePicker,
        title = { Text(stringResource(R.string.settings_time_picker_title)) },
        text = { TimePicker(state = picker) },
        confirmButton = {
            TextButton(
                onClick = { actions.onSetReminderTime(LocalTime.of(picker.hour, picker.minute)) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(CoreUiR.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = actions.onDismissTimePicker, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(CoreUiR.string.cancel))
            }
        },
    )
}

@Composable
private fun ImportDialog(summary: BackupSummary, actions: SettingsActions) {
    val numbers = remember { NumberFormat.getIntegerInstance() }
    val counts = stringResource(
        R.string.settings_import_counts,
        pluralStringResource(R.plurals.settings_import_machines, summary.machines, numbers.format(summary.machines)),
        pluralStringResource(R.plurals.settings_import_routines, summary.routines, numbers.format(summary.routines)),
        pluralStringResource(R.plurals.settings_import_trainings, summary.sessions, numbers.format(summary.sessions)),
        pluralStringResource(R.plurals.settings_import_sets, summary.sets, numbers.format(summary.sets)),
    )
    val range = remember(summary) { dateRange(summary.firstDay, summary.lastDay) }
    val message = if (range == null) {
        stringResource(R.string.settings_import_message, counts)
    } else {
        stringResource(R.string.settings_import_message_dates, counts, range)
    }
    AlertDialog(
        onDismissRequest = actions.onDismissImport,
        title = { Text(stringResource(R.string.settings_import_title)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = actions.onConfirmImport, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.settings_import_replace))
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onDismissImport, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(CoreUiR.string.cancel))
            }
        },
    )
}

/** "2 Sep – 2 Nov 2026", "2 Nov 2026" for one day, null for an empty backup. */
private fun dateRange(first: LocalDate?, last: LocalDate?): String? {
    if (first == null || last == null) return null
    val locale = Locale.getDefault()
    val full = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    if (first == last) return last.format(full)
    val start = if (first.year == last.year) {
        first.format(DateTimeFormatter.ofPattern("d MMM", locale))
    } else {
        first.format(full)
    }
    return "$start – ${last.format(full)}"
}
