package com.tuapp.tidal

import retrofit2.http.GET
import retrofit2.http.Query

interface MusicApiService {
    // Usamos una ruta de búsqueda más genérica y abierta
    @GET("search") 
    suspend fun searchTracks(
        @Query("q") query: String,
        @Query("limit") limit: Int = 10
    ): SearchResponse
}
