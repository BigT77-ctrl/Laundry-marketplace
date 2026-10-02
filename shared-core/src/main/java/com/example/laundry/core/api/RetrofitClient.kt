package com.example.laundry.core.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Singleton object that manages the Retrofit instance and provides access to the [LaundryApiService].
 */
object RetrofitClient {
    /** Base URL for the live admin backend used by the Android apps. */
    private const val BASE_URL = "http://10.0.2.2:4001/"

    /**
     * Lazily initialized [LaundryApiService] instance.
     * Uses Gson for JSON serialization/deserialization.
     */
    val apiService: LaundryApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LaundryApiService::class.java)
    }
}
