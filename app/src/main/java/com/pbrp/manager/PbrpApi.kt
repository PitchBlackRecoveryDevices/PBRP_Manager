package com.pbrp.manager

import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

interface PbrpApi {
    // 1. Fetch Builds JSON
    @GET("assets/json/builds-{codename}.json")
    suspend fun getBuilds(@Path("codename") codename: String): DeviceBuilds

    // 2. Fetch Device Info Markdown (We use ResponseBody because it is plain text)
    @GET("_oem/{vendor}/{codename}.md")
    suspend fun getDeviceInfo(
        @Path("vendor") vendor: String, 
        @Path("codename") codename: String
    ): ResponseBody

    companion object {
        // Pointing to Raw GitHub Content
        private const val BASE_URL = "https://raw.githubusercontent.com/PitchBlackRecoveryProject/PitchBlackRecoveryProject.github.io/refs/heads/pb/"

        fun create(): PbrpApi {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(PbrpApi::class.java)
        }
    }
}
