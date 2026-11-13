package com.fuzzydl.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import com.fuzzydl.app.data.AppLog
import com.fuzzydl.app.download.DownloadQueue

class App : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        DownloadQueue.init(this)
    }

    /** Coil com suporte a SVG (as capas do port-db são SVG/WebP). */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(SvgDecoder.Factory()) }
            .crossfade(180)
            .build()
}