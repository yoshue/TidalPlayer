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

        val searchInput = EditText(this).apply { 
            hint = "Escribe canción o artista..." 
            setSingleLine(true)
        }
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
                statusText.text = "Conectando con el servidor..."
                
                lifecycleScope.launch {
                    try {
                        val response = apiService.searchTracks(query)
                        val track = response.items.firstOrNull()
                        
                        if (track != null) {
                            statusText.text = "Cargando: ${track.title}"
                            val streamUrl = "https://clm-6.tidal.squid.wtf/api/download?id=${track.id}&quality=LOSSLESS"
                            
                            val mediaItem = MediaItem.fromUri(streamUrl)
                            player?.setMediaItem(mediaItem)
                            player?.prepare()
                            player?.play()
                        } else {
                            statusText.text = "Sin resultados para: $query"
                        }
                    } catch (e: Exception) {
                        // DETECTOR DE ERRORES: Esto nos dirá qué pasa realmente
                        statusText.text = "ERROR REAL: ${e.javaClass.simpleName} - ${e.message}"
                        e.printStackTrace()
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
