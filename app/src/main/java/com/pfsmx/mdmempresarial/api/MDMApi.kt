package com.pfsmx.mdmempresarial.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface MDMApi {

    @POST("devices/register")
    suspend fun registerDevice(
        @Body request: RegisterDeviceRequest
    ): RegisterDeviceResponse  // ⚠️ NO Response<...>

    @POST("devices/{deviceId}/sync")
    suspend fun syncPolicies(
        @Path("deviceId") deviceId: String,
        @Body request: DeviceSyncRequest
    ): PoliciesResponse  // ⚠️ NO Response<...>

    @GET("health")
    suspend fun health(): HealthResponse  // ⚠️ NO Response<...>
}