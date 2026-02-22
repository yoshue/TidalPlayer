package com.tuapp.tidal

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface MusicApiService {
    @GET("api/proxy/search") // Esta es la ruta que usa el repo oficial
    suspend fun searchTracks(
        @Query("query") query: String,
        @Query("type") type: String = "TRACKS",
        // Estas cabeceras son las que el servidor busca para no dar 404
        @Header("x-requested-with") requestedWith: String = "XMLHttpRequest",
        @Header("Accept") accept: String = "application/json"
    ): SearchResponse
}
