package com.tuapp.tidal

import retrofit2.http.GET
import retrofit2.http.Query

interface MusicApiService {
    // Usamos la ruta base de búsqueda del proxy
    @GET("api/proxy/search")
    suspend fun searchTracks(
        @Query("query") query: String,
        @Query("type") type: String = "TRACKS"
    ): SearchResponse
}
