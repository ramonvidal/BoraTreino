package com.boratreino.feature.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boratreino.core.model.BackupSummary
import com.boratreino.core.model.RestTimerRules
import com.boratreino.core.model.Sex
import com.boratreino.core.notify.NotificationPermission
import com.boratreino.core.settings.AppLanguage
import com.boratreino.core.ui.BoraButton
import com.boratreino.core.ui.BoraButtonStyle
import com.boratreino.core.ui.BoraCard
import com.boratreino.core.ui.BoraChip
import com.boratreino.core.ui.BoraSwitchColors
import com.boratreino.core.ui.ExerciseCredit
import com.boratreino.core.ui.SmallStepper
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
    val onStepGoal: (up: Boolean) -> Unit = {},
    val onRemindersEnabled: (Boolean) -> Unit = {},
    val onToggleWeekday: (DayOfWeek) -> Unit = {},
    val onOpenTimePicker: () -> Unit = {},
    val onDismissTimePicker: () -> Unit = {},
    val onSetReminderTime: (LocalTime) -> Unit = {},
    val onRestEnabled: (Boolean) -> Unit = {},
    val onRestSeconds: (Int) -> Unit = {},
    val onRestBeep: (Boolean) -> Unit = {},
    val onWarmupCalculator: (Boolean) -> Unit = {},
    val onAllowNotifications: () -> Unit = {},
    val onExport: () -> Unit = {},
    val onImport: () -> Unit = {},
    val onConfirmImport: () -> Unit = {},
    val onDismissImport: () -> Unit = {},
    val onChooseLanguage: (AppLanguage) -> Unit = {},
    val onHealthChange: (Boolean) -> Unit = {},
    val onGetHealthConnect: () -> Unit = {},
    val onBodySex: (Sex) -> Unit = {},
    val onOpenBodyField: (BodyField) -> Unit = {},
    val onConfirmBodyValue: (String) -> Unit = {},
    val onDismissBodyField: () -> Unit = {},
    val onBodyGoalStep: (up: Boolean) -> Unit = {},
    val onShowEnergyEstimate: (Boolean) -> Unit = {},
    val onTestRestAlarm: () -> Unit = {},
    val onOpenBatterySettings: () -> Unit = {},
)

@Composable
internal fun SettingsScreen(
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
            onStepGoal = viewModel::stepGoal,
            onRemindersEnabled = viewModel::setRemindersEnabled,
            onToggleWeekday = viewModel::toggleWeekday,
            onOpenTimePicker = viewModel::openTimePicker,
            onDismissTimePicker = viewModel::dismissTimePicker,
            onSetReminderTime = viewModel::setReminderTime,
            onRestEnabled = viewModel::setRestEnabled,
            onRestSeconds = viewModel::setRestSeconds,
            onRestBeep = viewModel::setRestBeep,
            onWarmupCalculator = viewModel::setWarmupCalculator,
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
            onBodySex = viewModel::setBodySex,
            onOpenBodyField = viewModel::openBodyField,
            onConfirmBodyValue = viewModel::confirmBodyValue,
            onDismissBodyField = viewModel::dismissBodyField,
            onBodyGoalStep = viewModel::stepBodyGoal,
            onShowEnergyEstimate = viewModel::setShowEnergyEstimate,
            onTestRestAlarm = viewModel::testRestAlarm,
            onOpenBatterySettings = {
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                    )
                } catch (e: ActivityNotFoundException) {
                    // No settings app: nothing to open.
                }
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

/** The sub-pages of Settings; the main screen is a list of rows that open them. */
internal enum class SettingsPage { TRAINING, BODY, DATA, APP }

/** Drawn from its state alone, so screenshot tests can render it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    updatesCard: (@Composable () -> Unit)? = null,
    initialPage: SettingsPage? = null,
) {
    var page by rememberSaveable { mutableStateOf(initialPage) }
    BackHandler(enabled = page != null) { page = null }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            val open = page
            TopAppBar(
                title = { Text(stringResource(if (open == null) R.string.settings_title else open.titleRes())) },
                navigationIcon = {
                    if (open != null) {
                        IconButton(onClick = { page = null }) {
                            Icon(
                                painterResource(CoreUiR.drawable.ic_back),
                                contentDescription = stringResource(CoreUiR.string.back),
                            )
                        }
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
                when (page) {
                    null -> {
                        SettingsPage.entries.forEach { entry ->
                            item(key = entry.name) {
                                PageRow(
                                    title = stringResource(entry.titleRes()),
                                    summary = entry.summary(state),
                                    onClick = { page = entry },
                                )
                            }
                        }
                    }
                    SettingsPage.TRAINING -> {
                        item { GoalSection(state, actions) }
                        item { RemindersSection(state, actions) }
                        if (state.showNotificationsSection) item { NotificationsSection(actions) }
                        item { RestTimerSection(state, actions) }
                        item { WarmupSection(state, actions) }
                    }
                    SettingsPage.BODY -> item { BodySection(state.body, actions) }
                    SettingsPage.DATA -> {
                        item { BackupSection(actions) }
                        if (state.health != HealthCardState.Hidden) item { HealthSection(state.health, actions) }
                    }
                    SettingsPage.APP -> {
                        if (updatesCard != null) item { updatesCard() }
                        item { LanguageSection(state, actions) }
                        item { AboutSection(state) }
                    }
                }
            }
        }
    }

    if (state.timePickerOpen) TimeDialog(state, actions)
    if (state.body.dialog != null) BodyDialog(state.body, state.body.dialog, actions)
    if (state.importConfirm != null) ImportDialog(state.importConfirm, actions)
}

private fun SettingsPage.titleRes(): Int = when (this) {
    SettingsPage.TRAINING -> R.string.settings_section_training
    SettingsPage.BODY -> R.string.settings_body_title
    SettingsPage.DATA -> R.string.settings_section_data
    SettingsPage.APP -> R.string.settings_section_app
}

/** The muted line under a row's title: what the page currently holds. */
@Composable
private fun SettingsPage.summary(state: SettingsUiState): String = when (this) {
    SettingsPage.TRAINING -> stringResource(
        R.string.settings_training_summary,
        pluralStringResource(R.plurals.settings_summary_goal, state.weeklyGoal, state.weeklyGoal),
        if (state.restTimer.enabled) {
            stringResource(R.string.settings_summary_rest, state.restTimer.defaultSeconds)
        } else {
            stringResource(R.string.settings_summary_rest_off)
        },
    )
    SettingsPage.BODY -> {
        val weight = state.body.draft.weightKg
        val height = state.body.draft.heightCm
        if (weight == null || height == null) {
            stringResource(R.string.settings_body_not_set)
        } else {
            stringResource(R.string.settings_body_summary, weight.plain(), height)
        }
    }
    SettingsPage.DATA -> stringResource(R.string.settings_data_summary)
    SettingsPage.APP -> stringResource(R.string.settings_app_summary, state.language.label, state.installedVersion)
}

/** One row of the main list: title, current value, chevron. */
@Composable
private fun PageRow(title: String, summary: String, onClick: () -> Unit) {
    BoraCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                painterResource(CoreUiR.drawable.ic_expand_more),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(-90f),
            )
        }
    }
}

@Composable
internal fun SectionCard(title: String, content: @Composable () -> Unit) {
    BoraCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, colors = BoraSwitchColors())
    }
}

@Composable
internal fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun SettingsChip(label: String, selected: Boolean, onClick: () -> Unit) {
    BoraChip(label = label, selected = selected, onClick = onClick)
}

@Composable
private fun GoalSection(state: SettingsUiState, actions: SettingsActions) {
    SectionCard(stringResource(R.string.settings_goal_title)) {
        val label = stringResource(R.string.settings_goal_label)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            SmallStepper(
                value = state.weeklyGoal.toString(),
                onMinus = { actions.onStepGoal(false) },
                onPlus = { actions.onStepGoal(true) },
                contentDescription = label,
            )
        }
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

@Composable
private fun WarmupSection(state: SettingsUiState, actions: SettingsActions) {
    SectionCard(stringResource(R.string.settings_warmup_title)) {
        SwitchRow(stringResource(R.string.settings_warmup_switch), state.warmupCalculator, actions.onWarmupCalculator)
        Hint(stringResource(R.string.settings_warmup_hint))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RestTimerSection(state: SettingsUiState, actions: SettingsActions) {
    SectionCard(stringResource(R.string.settings_rest_title)) {
        SwitchRow(stringResource(R.string.settings_rest_switch), state.restTimer.enabled, actions.onRestEnabled)
        if (state.restTimer.enabled) {
            SwitchRow(stringResource(R.string.settings_rest_beep), state.restTimer.beep, actions.onRestBeep)
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
            Text(
                stringResource(R.string.settings_rest_check),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BoraButton(
                text = stringResource(R.string.settings_rest_test),
                onClick = actions.onTestRestAlarm,
                style = BoraButtonStyle.Tonal,
            )
            Hint(stringResource(R.string.settings_rest_test_hint))
            TextButton(onClick = actions.onOpenBatterySettings) {
                Text(stringResource(R.string.settings_rest_battery))
            }
            Hint(stringResource(R.string.settings_rest_battery_hint))
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
                BoraButton(
                    text = stringResource(R.string.settings_health_get),
                    onClick = actions.onGetHealthConnect,
                    modifier = Modifier.fillMaxWidth(),
                )
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
            BoraButton(
                text = stringResource(R.string.settings_notifications_allow),
                onClick = actions.onAllowNotifications,
                style = BoraButtonStyle.Tonal,
            )
        }
    }
}

@Composable
private fun BackupSection(actions: SettingsActions) {
    SectionCard(stringResource(R.string.settings_backup_title)) {
        Hint(stringResource(R.string.settings_backup_hint))
        BoraButton(
            text = stringResource(R.string.settings_backup_export),
            onClick = actions.onExport,
            modifier = Modifier.fillMaxWidth(),
        )
        BoraButton(
            text = stringResource(R.string.settings_backup_import),
            onClick = actions.onImport,
            modifier = Modifier.fillMaxWidth(),
            style = BoraButtonStyle.Tonal,
        )
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
