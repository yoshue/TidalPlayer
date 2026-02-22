package com.tuapp.tidal

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URLEncoder

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
            setPadding(60, 60, 60, 60)
        }

        val searchInput = EditText(this).apply { 
            hint = "Canción o Artista..." 
            setSingleLine(true)
        }
        val searchButton = Button(this).apply { text = "BUSCAR Y REPRODUCIR" }
        val statusText = TextView(this).apply { 
            text = "Listo para iniciar"
            setPadding(0, 20, 0, 0)
        }

        layout.addView(searchInput)
        layout.addView(searchButton)
        layout.addView(statusText)
        setContentView(layout)

        player = ExoPlayer.Builder(this).build()

        searchButton.setOnClickListener {
            val rawQuery = searchInput.text.toString()
            if (rawQuery.isNotEmpty()) {
                statusText.text = "Buscando..."
                
                lifecycleScope.launch {
                    try {
                        // Limpiamos la búsqueda para evitar el error 404 por caracteres raros
                        val cleanQuery = URLEncoder.encode(rawQuery, "UTF-8")
                        
                        val response = apiService.searchTracks(cleanQuery)
                        val tracks = response.items
                        
                        if (tracks.isNotEmpty()) {
                            val track = tracks[0]
                            statusText.text = "Sonando: ${track.title}\nArtista: ${track.artist.name}"
                            
                            val streamUrl = "https://clm-6.tidal.squid.wtf/api/download?id=${track.id}&quality=LOSSLESS"
                            
                            player?.setMediaItem(MediaItem.fromUri(streamUrl))
                            player?.prepare()
                            player?.play()
                        } else {
                            statusText.text = "No se encontró nada para: $rawQuery"
                        }
                    } catch (e: Exception) {
                        // Si sale 404 aquí, es que el servidor cambió la ruta interna
                        statusText.text = "Error del servidor: ${e.message}"
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
