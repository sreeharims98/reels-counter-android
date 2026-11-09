package com.reelscounter

import android.app.Application
import com.reelscounter.data.AppDatabase
import com.reelscounter.data.SettingsRepository
import com.reelscounter.data.StatsRepository

class ReelsApp : Application() {
    val database by lazy { AppDatabase.create(this) }
    val stats by lazy { StatsRepository(database.reelDao()) }
    val settings by lazy { SettingsRepository(this) }
}
