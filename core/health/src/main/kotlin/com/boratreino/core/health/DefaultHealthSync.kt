package com.boratreino.core.health

import android.util.Log
import com.boratreino.core.data.HistoryRepository
import com.boratreino.core.model.HealthExport
import com.boratreino.core.settings.HealthSettings
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@Singleton
internal class DefaultHealthSync @Inject constructor(
    private val access: HealthConnectAccess,
    private val history: HistoryRepository,
    private val settings: HealthSettings,
) : HealthSync {
    private val started = AtomicBoolean(false)

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    override fun start(scope: CoroutineScope) {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            settings.enabled
                .distinctUntilChanged()
                .flatMapLatest { on ->
                    if (on) history.observeClosedSessionExports(SYNC_DAYS).debounce(DEBOUNCE_MS) else emptyFlow()
                }
                .collect { exports -> syncSafely(exports) }
        }
    }

    private suspend fun syncSafely(exports: List<HealthExport>) {
        try {
            sync(exports)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Never crash the app; the next emission tries again.
            Log.w(TAG, "Health Connect sync failed", e)
        }
    }

    private suspend fun sync(exports: List<HealthExport>) {
        if (access.availability() != HealthAvailability.Ready) return
        access.write(exports)
        val existing = history.observeSessionIds().first()
        val synced = settings.syncedSessionIds.first()
        val gone = synced.filterTo(mutableSetOf()) { it !in existing }
        if (gone.isNotEmpty()) access.delete(gone)
        settings.setSyncedSessionIds((synced - gone) + exports.map { it.sessionId })
    }

    private companion object {
        const val TAG = "HealthSync"
        const val SYNC_DAYS = 30
        const val DEBOUNCE_MS = 3_000L
    }
}
