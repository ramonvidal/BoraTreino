package com.boratreino.feature.settings

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boratreino.core.data.BackupPreview
import com.boratreino.core.data.BackupRepository
import com.boratreino.core.model.BackupData
import com.boratreino.core.model.BackupSummary
import com.boratreino.core.model.ReminderSettings
import com.boratreino.core.model.RestTimerRules
import com.boratreino.core.model.RestTimerSettings
import com.boratreino.core.model.WeekGoalRules
import com.boratreino.core.notify.ReminderScheduler
import com.boratreino.core.settings.AppLanguage
import com.boratreino.core.settings.TrainingSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Everything the Settings screen shows. [notificationsGranted] is what the system says right now;
 * the notifications section appears only when something needs them and they are missing.
 * [importConfirm] is the summary of a backup waiting for the replace confirmation (null = closed).
 */
internal data class SettingsUiState(
    val loaded: Boolean = false,
    val weeklyGoal: Int = WeekGoalRules.DEFAULT_GOAL,
    val reminders: ReminderSettings = ReminderSettings(),
    val restTimer: RestTimerSettings = RestTimerSettings(),
    val notificationsGranted: Boolean = true,
    val timePickerOpen: Boolean = false,
    val is24Hour: Boolean = true,
    val importConfirm: BackupSummary? = null,
    val installedVersion: String = "",
    val language: AppLanguage = AppLanguage.ENGLISH,
) {
    val showNotificationsSection: Boolean
        get() = (reminders.enabled || restTimer.enabled) && !notificationsGranted
}

internal sealed interface SettingsEvent {
    data object BackupSaved : SettingsEvent
    data object BackupImported : SettingsEvent
    data object InvalidBackup : SettingsEvent
    data object NewerBackup : SettingsEvent
    data object Failed : SettingsEvent
}

/** Things only the screen owns, merged into the state next to the stored settings. */
private data class Overlay(
    val notificationsGranted: Boolean = true,
    val timePickerOpen: Boolean = false,
    val importConfirm: BackupSummary? = null,
    val language: AppLanguage = AppLanguage.current(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: TrainingSettings,
    private val backup: BackupRepository,
    private val reminderScheduler: ReminderScheduler,
    @ApplicationContext private val context: Context,
    @Suppress("unused") private val clock: Clock,
) : ViewModel() {

    private val eventChannel = Channel<SettingsEvent>(Channel.BUFFERED)
    internal val events: Flow<SettingsEvent> = eventChannel.receiveAsFlow()

    private val overlay = MutableStateFlow(Overlay())

    private val installedVersion: String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    } catch (e: Exception) {
        ""
    }

    /** The parsed backup behind the confirm dialog. */
    private var pendingImport: BackupData? = null

    internal val state: StateFlow<SettingsUiState> = combine(
        settings.weeklyGoal,
        settings.reminders,
        settings.restTimer,
        overlay,
    ) { goal, reminders, restTimer, overlay ->
        SettingsUiState(
            loaded = true,
            weeklyGoal = goal,
            reminders = reminders,
            restTimer = restTimer,
            notificationsGranted = overlay.notificationsGranted,
            timePickerOpen = overlay.timePickerOpen,
            importConfirm = overlay.importConfirm,
            installedVersion = installedVersion,
            language = overlay.language,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun chooseLanguage(language: AppLanguage) {
        overlay.update { it.copy(language = language) }
        AppLanguage.choose(language)
    }

    fun suggestedFileName(): String = backup.suggestedFileName()

    fun setNotificationsGranted(granted: Boolean) {
        overlay.update { it.copy(notificationsGranted = granted) }
    }

    fun stepGoal(up: Boolean) = save {
        val goal = settings.weeklyGoal.first()
        settings.setWeeklyGoal((if (up) goal + 1 else goal - 1).coerceIn(WeekGoalRules.MIN_GOAL, WeekGoalRules.MAX_GOAL))
    }

    fun setRemindersEnabled(enabled: Boolean) = changeReminders { it.copy(enabled = enabled) }

    fun toggleWeekday(day: DayOfWeek) = changeReminders { current ->
        val days = if (day in current.weekdays) current.weekdays - day else current.weekdays + day
        // At least one day stays on, or the reminder could never fire.
        if (days.isEmpty()) current else current.copy(weekdays = days)
    }

    fun setReminderTime(time: LocalTime) {
        overlay.update { it.copy(timePickerOpen = false) }
        changeReminders { it.copy(time = time) }
    }

    fun openTimePicker() = overlay.update { it.copy(timePickerOpen = true) }

    fun dismissTimePicker() = overlay.update { it.copy(timePickerOpen = false) }

    fun setRestEnabled(enabled: Boolean) = save {
        settings.setRestTimer(settings.restTimer.first().copy(enabled = enabled))
    }

    fun setRestSeconds(seconds: Int) = save {
        settings.setRestTimer(
            settings.restTimer.first().copy(
                defaultSeconds = seconds.coerceIn(RestTimerRules.MIN_SECONDS, RestTimerRules.MAX_SECONDS),
            ),
        )
    }

    /** Writes the whole backup to [uri], a document the user just picked. */
    fun exportTo(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            try {
                val text = backup.export()
                withContext(Dispatchers.IO) {
                    val stream = resolver.openOutputStream(uri, "wt") ?: error("No output stream")
                    stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
                eventChannel.trySend(SettingsEvent.BackupSaved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                eventChannel.trySend(SettingsEvent.Failed)
            }
        }
    }

    /** Reads the file at [uri] and, when it is a usable backup, asks for the replace confirmation. */
    fun readBackup(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            try {
                val preview = withContext(Dispatchers.IO) {
                    val stream = resolver.openInputStream(uri) ?: error("No input stream")
                    val text = stream.use { it.readBytes().toString(Charsets.UTF_8) }
                    backup.preview(text)
                }
                when (preview) {
                    is BackupPreview.Ready -> {
                        pendingImport = preview.data
                        overlay.update { it.copy(importConfirm = preview.summary) }
                    }
                    is BackupPreview.Invalid -> eventChannel.trySend(SettingsEvent.InvalidBackup)
                    is BackupPreview.TooNew -> eventChannel.trySend(SettingsEvent.NewerBackup)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                eventChannel.trySend(SettingsEvent.Failed)
            }
        }
    }

    fun dismissImport() {
        pendingImport = null
        overlay.update { it.copy(importConfirm = null) }
    }

    fun confirmImport() {
        val data = pendingImport ?: return
        dismissImport()
        viewModelScope.launch {
            try {
                backup.import(data)
                eventChannel.trySend(SettingsEvent.BackupImported)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                eventChannel.trySend(SettingsEvent.Failed)
            }
        }
    }

    private fun changeReminders(change: (ReminderSettings) -> ReminderSettings) = save {
        settings.setReminders(change(settings.reminders.first()))
        reminderScheduler.reschedule()
    }

    private fun save(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                eventChannel.trySend(SettingsEvent.Failed)
            }
        }
    }
}
