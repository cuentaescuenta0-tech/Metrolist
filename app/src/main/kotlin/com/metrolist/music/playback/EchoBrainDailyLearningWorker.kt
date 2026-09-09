/*
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.playback

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.metrolist.music.constants.EchoBrainDailyLearningEnabledKey
import com.metrolist.music.constants.EchoBrainLastLearningDayKey
import com.metrolist.music.constants.EchoBrainNeuroProfileKey
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.safeDataStoreEdit
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Performs one bounded, local FlowNeuro maintenance pass per calendar day. */
class EchoBrainDailyLearningWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val preferences = applicationContext.dataStore.data.first()
        if (preferences[EchoBrainDailyLearningEnabledKey] == false) return Result.success()

        val today = LocalDate.now().toString()
        if (preferences[EchoBrainLastLearningDayKey] == today) return Result.success()

        val profile = EchoBrainNeuroProfile().apply {
            restore(preferences[EchoBrainNeuroProfileKey].orEmpty())
        }
        val changed = profile.consolidateDaily()
        val saved = applicationContext.safeDataStoreEdit { mutablePreferences ->
            if (changed) mutablePreferences[EchoBrainNeuroProfileKey] = profile.serialize()
            mutablePreferences[EchoBrainLastLearningDayKey] = today
        }
        return if (saved) Result.success() else Result.retry()
    }
}

object EchoBrainDailyLearningScheduler {
    private const val WORK_NAME = "echo-brain-daily-learning"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<EchoBrainDailyLearningWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(24, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
