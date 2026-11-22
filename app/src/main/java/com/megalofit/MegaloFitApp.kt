package com.megalofit

import android.app.Application
import com.megalofit.data.AppDatabase
import com.megalofit.data.FitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MegaloFitApp : Application() {
    /** Scope de toda la app, para escrituras que deben terminar aunque se cierre la pantalla. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var repository: FitRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = FitRepository(AppDatabase.build(this), this)
        appScope.launch { repository.syncSeedExercises() }
    }
}
