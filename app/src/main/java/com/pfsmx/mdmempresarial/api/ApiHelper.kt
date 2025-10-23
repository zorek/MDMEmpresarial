package com.pfsmx.mdmempresarial.api

import android.util.Log
import retrofit2.HttpException
import java.io.IOException

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val code: Int? = null) : ApiResult<Nothing>()
}

object ApiHelper {
    private const val TAG = "ApiHelper"

    suspend fun <T> safeApiCall(apiCall: suspend () -> T): ApiResult<T> {
        return try {
            val response = apiCall.invoke()
            ApiResult.Success(response)

        } catch (e: HttpException) {
            val errorMessage = when (e.code()) {
                400 -> "Solicitud inválida"
                401 -> "No autorizado"
                403 -> "Acceso prohibido"
                404 -> "Recurso no encontrado"
                500 -> "Error del servidor"
                503 -> "Servicio no disponible"
                else -> "Error HTTP: ${e.code()}"
            }

            Log.e(TAG, "❌ HTTP Error: $errorMessage", e)
            ApiResult.Error(errorMessage, e.code())

        } catch (e: IOException) {
            Log.e(TAG, "❌ Network Error", e)
            ApiResult.Error("Error de red: ${e.message}")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Unexpected Error", e)
            ApiResult.Error("Error inesperado: ${e.message}")
        }
    }
}