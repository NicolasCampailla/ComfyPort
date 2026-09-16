package com.comfyport.network.http

import com.google.gson.Gson
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Central HTTP infrastructure for ComfyPort.
 * Provides the shared OkHttpClient, Gson instance, and common media types.
 * All network modules should use this instead of creating their own clients.
 */
object ComfyHttpClient {

    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val gson: Gson = Gson()

    val jsonMediaType: MediaType = "application/json; charset=utf-8".toMediaType()

    val imageMediaType: MediaType = "image/png".toMediaType()

    /**
     * Evicts all idle connections from the shared connection pool.
     * Useful when switching servers or recovering from stale connections.
     */
    fun evictConnectionPool() {
        try {
            okHttp.connectionPool.evictAll()
        } catch (_: Exception) { }
    }
}
