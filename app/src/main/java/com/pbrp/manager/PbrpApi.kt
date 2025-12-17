package com.pbrp.manager

import com.google.gson.JsonObject
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Url

interface PbrpApi {
    // 1. Fetch Builds JSON (From Devices Repo)
    @GET("https://raw.githubusercontent.com/PitchBlackRecoveryProject/PitchBlackRecoveryProject.github.io/refs/heads/pb/assets/json/builds-{codename}.json")
    suspend fun getBuilds(@Path("codename") codename: String): DeviceBuilds

    // 2. Fetch Device Info Markdown (From Devices Repo)
    @GET("https://raw.githubusercontent.com/PitchBlackRecoveryProject/PitchBlackRecoveryProject.github.io/refs/heads/pb/_oem/{vendor}/{codename}.md")
    suspend fun getDeviceInfo(@Path("vendor") vendor: String, @Path("codename") codename: String): ResponseBody

    // 3. Fetch Full Device List (From Vendor Utils Repo) for Search
    @GET("https://raw.githubusercontent.com/PitchBlackRecoveryProject/vendor_utils/pb/pb_devices.json")
    suspend fun getAllDevices(): JsonObject

    companion object {
        fun create(): PbrpApi {
            return Retrofit.Builder()
                .baseUrl("https://localhost/") // Base URL ignored because we use full URLs above
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(PbrpApi::class.java)
        }
    }
}
