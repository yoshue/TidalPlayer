package com.tuapp.tidal

data class SearchResponse(val items: List<Track>)
data class Track(val id: String, val title: String, val artist: Artist)
data class Artist(val name: String)
