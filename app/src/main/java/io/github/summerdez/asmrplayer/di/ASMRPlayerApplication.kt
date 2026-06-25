package io.github.summerdez.asmrplayer.di

import android.app.Application
import io.github.summerdez.asmrplayer.data.update.cleanInstalledUpdateCache

class ASMRPlayerApplication : Application() {
    val appContainer: AppContainer by lazy {
        AppContainer(this)
    }

    override fun onCreate() {
        super.onCreate()
        cleanInstalledUpdateCache()
    }
}
