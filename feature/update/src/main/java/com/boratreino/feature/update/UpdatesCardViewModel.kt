package com.boratreino.feature.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boratreino.core.update.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The quiet line under "Check for updates". */
internal sealed interface UpdatesCardStatus {
    data object Idle : UpdatesCardStatus
    data object Checking : UpdatesCardStatus
    data object UpToDate : UpdatesCardStatus
    data class Available(val version: String) : UpdatesCardStatus
    data object Unreachable : UpdatesCardStatus
}

/** What the "App updates" card shows. */
internal data class UpdatesCardUiState(
    val installedVersion: String = "",
    val status: UpdatesCardStatus = UpdatesCardStatus.Idle,
)

/** Backs the "App updates" card: the installed version and the manual "Check for updates". */
@HiltViewModel
class UpdatesCardViewModel @Inject constructor(
    private val updates: UpdateRepository,
) : ViewModel() {

    private val installedVersion: String = try {
        updates.installedVersion
    } catch (e: Exception) {
        ""
    }

    private val _state = MutableStateFlow(UpdatesCardUiState(installedVersion = installedVersion))
    internal val state: StateFlow<UpdatesCardUiState> = _state

    /** Asks the release page right away; the banner shows a found version wherever it is. */
    fun checkForUpdates() {
        _state.value = _state.value.copy(status = UpdatesCardStatus.Checking)
        viewModelScope.launch {
            val status = try {
                val found = updates.checkNow()
                if (found == null) UpdatesCardStatus.UpToDate else UpdatesCardStatus.Available(found.versionName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UpdatesCardStatus.Unreachable
            }
            _state.value = _state.value.copy(status = status)
        }
    }
}
