package com.wyrix.wallpaper.network

import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val BASE_URL = "https://pixabay.com/"

    // Pixabay's API terms require responses to be cached for 24 hours.
    private const val CACHE_SECONDS = 24 * 60 * 60
    private const val CACHE_SIZE_BYTES = 10L * 1024 * 1024

    /** Set once from Application.onCreate(), before [api] is first used. */
    @Volatile
    var cacheDir: File? = null

    val api: PixabayApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                response.newBuilder()
                    .removeHeader("Pragma")
                    .header("Cache-Control", "public, max-age=$CACHE_SECONDS")
                    .build()
            }
            .apply {
                cacheDir?.let { cache(Cache(File(it, "pixabay_api"), CACHE_SIZE_BYTES)) }
            }
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PixabayApi::class.java)
    }
}
