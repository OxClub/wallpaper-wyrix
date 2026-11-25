package com.wyrix.wallpaper.ads

import android.content.Context
import android.util.Log
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.UnityAds
import com.wyrix.wallpaper.BuildConfig

/**
 * Unity Ads initialisation (banner only). Debug builds run in TEST mode (no live ads);
 * release builds run live.
 */
object UnityAdsManager {
    private const val TAG = "UnityAds"

    val isEnabled: Boolean get() = BuildConfig.UNITY_GAME_ID.isNotBlank()

    @Volatile
    var isInitialized = false
        private set

    fun init(context: Context) {
        if (!isEnabled) return
        if (UnityAds.isInitialized) {
            isInitialized = true
            return
        }
        UnityAds.initialize(
            context.applicationContext,
            BuildConfig.UNITY_GAME_ID,
            BuildConfig.DEBUG, // testMode
            object : IUnityAdsInitializationListener {
                override fun onInitializationComplete() {
                    isInitialized = true
                }

                override fun onInitializationFailed(
                    error: UnityAds.UnityAdsInitializationError?,
                    message: String?,
                ) {
                    Log.w(TAG, "Init failed: $error $message")
                }
            },
        )
    }
}
