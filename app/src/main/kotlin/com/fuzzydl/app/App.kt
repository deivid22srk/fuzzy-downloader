package com.fuzzydl.app

import android.app.Application
import com.fuzzydl.app.data.AppLog
import com.fuzzydl.app.data.StateStore
import com.fuzzydl.app.download.DownloadEngine
import com.fuzzydl.app.shizuku.ShizukuManager
import com.fuzzydl.app.store.StoreDownloads

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        StateStore.init(this)
        ShizukuManager.init(this)
        DownloadEngine.init(this)
        StoreDownloads.init(this)
    }
}
