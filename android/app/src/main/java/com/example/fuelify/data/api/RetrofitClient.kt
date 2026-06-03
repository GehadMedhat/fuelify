package com.example.fuelify.data.api

import android.util.Log
import com.example.fuelify.auth.network.RefreshRequest
import com.example.fuelify.auth.network.RetrofitClient as AuthRetrofitClient
import com.example.fuelify.auth.network.SessionManager
import com.example.fuelify.statistics.HealthApiService
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val BASE_URL = "http://192.168.1.3:8080/"
    private const val TAG = "AuthInterceptor"

    private val client: OkHttpClient by lazy {

        val authInterceptor = Interceptor { chain ->

            val token        = SessionManager.getToken()
            val refreshToken = SessionManager.getRefreshToken()

            Log.d(TAG, "──────────────────────────────────────────")
            Log.d(TAG, "Request : ${chain.request().method} ${chain.request().url}")
            Log.d(TAG, "Token   : ${if (token != null) token.take(30) + "..." else "NULL ⚠️"}")
            Log.d(TAG, "Refresh : ${if (refreshToken != null) refreshToken.take(30) + "..." else "NULL ⚠️"}")

            val originalRequest = chain.request().newBuilder()
                .apply { if (token != null) addHeader("Authorization", "Bearer $token") }
                .build()

            val response = chain.proceed(originalRequest)
            Log.d(TAG, "Response: ${response.code} ${response.message}")

            if (response.code == 401) {
                response.close()
                Log.w(TAG, "Got 401 — attempting silent token refresh...")

                if (refreshToken == null) {
                    Log.e(TAG, "No refresh token available — clearing session ❌")
                    SessionManager.clear()
                    chain.proceed(originalRequest)
                } else {
                    val refreshResponse = runBlocking {
                        try {
                            Log.d(TAG, "Calling refresh endpoint...")
                            val r = AuthRetrofitClient.instance.refresh(RefreshRequest(refreshToken))
                            Log.d(TAG, "Refresh HTTP status : ${r.code()}")
                            Log.d(TAG, "Refresh body success: ${r.body()?.success}")
                            r
                        } catch (e: Exception) {
                            Log.e(TAG, "Refresh call threw an exception ❌", e)
                            null
                        }
                    }

                    val newToken = refreshResponse
                        ?.takeIf { it.isSuccessful && it.body()?.success == true }
                        ?.body()?.data?.accessToken

                    if (newToken != null) {
                        Log.d(TAG, "Refresh succeeded ✅ — new token: ${newToken.take(30)}...")
                        SessionManager.saveToken(newToken)
                        chain.proceed(
                            chain.request().newBuilder()
                                .addHeader("Authorization", "Bearer $newToken")
                                .build()
                        )
                    } else {
                        Log.e(TAG, "Refresh failed — newToken is null — clearing session ❌")
                        Log.e(TAG, "Refresh body was: ${refreshResponse?.body()}")
                        SessionManager.clear()
                        chain.proceed(originalRequest)
                    }
                }
            } else {
                response
            }
        }

        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val api: FuelifyApi by lazy { retrofit.create(FuelifyApi::class.java) }

    val healthApi: HealthApiService by lazy { retrofit.create(HealthApiService::class.java) }
}
