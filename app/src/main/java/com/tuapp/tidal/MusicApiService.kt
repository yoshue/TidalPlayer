package com.tuapp.tidal

import retrofit2.http.GET
import retrofit2.http.Query

interface MusicApiService {
    @GET("search")
    suspend fun searchTracks(
        @Query("query") query: String,
        @Query("type") type: String = "TRACKS"
    ): SearchResponse
}
