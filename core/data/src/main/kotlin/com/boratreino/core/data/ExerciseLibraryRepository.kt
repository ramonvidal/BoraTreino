package com.boratreino.core.data

import com.boratreino.core.model.LibraryExercise

interface ExerciseLibraryRepository {
    /** Loaded once from assets/exercise_library.json, then cached; empty when the asset is unreadable. */
    suspend fun all(): List<LibraryExercise>
    suspend fun byId(id: String): LibraryExercise?
}
