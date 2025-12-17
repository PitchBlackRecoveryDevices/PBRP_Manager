package com.pbrp.manager

import com.google.gson.JsonObject
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

interface PbrpApi {
    @GET("https://raw.githubusercontent.com/PitchBlackRecoveryProject/PitchBlackRecoveryProject.github.io/pb/assets/json/builds-{codename}.json")
    suspend fun getBuilds(@Path("codename") codename: String): DeviceBuilds

    @GET("https://raw.githubusercontent.com/PitchBlackRecoveryProject/PitchBlackRecoveryProject.github.io/pb/_oem/{vendor}/{codename}.md")
    suspend fun getDeviceInfo(@Path("vendor") vendor: String, @Path("codename") codename: String): ResponseBody

    @GET("https://raw.githubusercontent.com/PitchBlackRecoveryProject/vendor_utils/pb/pb_devices.json")
    suspend fun getAllDevices(): JsonObject

    companion object {
        fun create(): PbrpApi {
            return Retrofit.Builder()
                .baseUrl("https://localhost/") 
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(PbrpApi::class.java)
        }
    }
}
