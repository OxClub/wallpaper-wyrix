package com.wyrix.wallpaper

import android.app.Application
import com.wyrix.wallpaper.ads.UnityAdsManager

class WallpaperApp : Application() {

    override fun onCreate() {
        super.onCreate()
        com.wyrix.wallpaper.network.RetrofitClient.cacheDir = cacheDir
        UnityAdsManager.init(this)
    }
}
