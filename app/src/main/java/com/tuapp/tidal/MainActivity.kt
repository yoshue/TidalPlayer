package com.tuapp.tidal

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private val apiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://tidal.squid.wtf/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MusicApiService::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        val searchInput = EditText(this).apply { hint = "Nombre de canción o artista..." }
        val searchButton = Button(this).apply { text = "BUSCAR Y REPRODUCIR" }
        val statusText = TextView(this).apply { text = "Listo para buscar" }

        layout.addView(searchInput)
        layout.addView(searchButton)
        layout.addView(statusText)
        setContentView(layout)

        player = ExoPlayer.Builder(this).build()

        searchButton.setOnClickListener {
            val query = searchInput.text.toString()
            if (query.isNotEmpty()) {
                statusText.text = "Buscando: $query..."
                
                // Ejecutamos la búsqueda en segundo plano
                lifecycleScope.launch {
                    try {
                        val response = apiService.searchTracks(query)
                        val firstTrack = response.items.firstOrNull()

                        if (firstTrack != null) {
                            statusText.text = "Reproduciendo: ${firstTrack.title} - ${firstTrack.artist.name}"
                            
                            // Construimos la URL de streaming usando el ID de la canción
                            val streamUrl = "https://clm-6.tidal.squid.wtf/api/download?id=${firstTrack.id}&quality=LOSSLESS"
                            
                            val mediaItem = MediaItem.fromUri(streamUrl)
                            player?.setMediaItem(mediaItem)
                            player?.prepare()
                            player?.play()
                        } else {
                            statusText.text = "No se encontraron resultados."
                        }
                    } catch (e: Exception) {
                        statusText.text = "Error: ${e.message}"
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
    }
}
