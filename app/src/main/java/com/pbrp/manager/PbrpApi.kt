package com.pbrp.manager

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

interface PbrpApi {
    // Fetches: https://pitchblackrecoverydevices.github.io/assets/json/builds-{codename}.json
    @GET("assets/json/builds-{codename}.json")
    suspend fun getBuilds(@Path("codename") codename: String): DeviceBuilds

    companion object {
        // FIXED: Using the DEVICES domain which hosts the JSON files
        private const val BASE_URL = "https://pitchblackrecoverydevices.github.io/"

        fun create(): PbrpApi {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(PbrpApi::class.java)
        }
    }
}
