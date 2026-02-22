package com.tuapp.tidal

import retrofit2.http.GET
import retrofit2.http.Query

interface MusicApiService {
    // Cambiamos la ruta de "api/proxy/search" a solo "search" o la ruta directa
    @GET("search") 
    suspend fun searchTracks(
        @Query("query") query: String,
        @Query("type") type: String = "TRACKS"
    ): SearchResponse
}
