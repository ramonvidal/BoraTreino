package com.boratreino.core.data

import android.content.Context
import com.boratreino.core.model.ExerciseLibraryRules
import com.boratreino.core.model.LibraryExercise
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DefaultExerciseLibraryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : ExerciseLibraryRepository {
    private val mutex = Mutex()
    private var cache: List<LibraryExercise>? = null
    private var byId: Map<String, LibraryExercise> = emptyMap()

    override suspend fun all(): List<LibraryExercise> {
        cache?.let { return it }
        return mutex.withLock {
            cache ?: withContext(Dispatchers.Default) {
                val list = try {
                    context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { ExerciseLibraryRules.parse(it.readText()) }
                } catch (e: Exception) {
                    emptyList()
                }
                byId = list.associateBy { it.id }
                list
            }.also { cache = it }
        }
    }

    override suspend fun byId(id: String): LibraryExercise? {
        all()
        return byId[id]
    }

    private companion object {
        const val ASSET = "exercise_library.json"
    }
}
