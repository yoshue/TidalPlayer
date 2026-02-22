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
            hint = "Nombre de canción..." 
            setSingleLine(true)
        }
        val searchButton = Button(this).apply { text = "BUSCAR Y REPRODUCIR" }
        val statusText = TextView(this).apply { text = "Estado: Esperando búsqueda" }

        layout.addView(searchInput)
        layout.addView(searchButton)
        layout.addView(statusText)
        setContentView(layout)

        player = ExoPlayer.Builder(this).build()

        searchButton.setOnClickListener {
            val query = searchInput.text.toString()
            if (query.isNotEmpty()) {
                statusText.text = "Buscando en Tidal..."
                
                lifecycleScope.launch {
                    try {
                        val response = apiService.searchTracks(query)
                        val track = response.items.firstOrNull()
                        
                        if (track != null) {
                            statusText.text = "Reproduciendo: ${track.title}"
                            // Usamos el ID para generar la URL de descarga
                            val streamUrl = "https://clm-6.tidal.squid.wtf/api/download?id=${track.id}&quality=LOSSLESS"
                            
                            val mediaItem = MediaItem.fromUri(streamUrl)
                            player?.setMediaItem(mediaItem)
                            player?.prepare()
                            player?.play()
                        } else {
                            statusText.text = "No se encontraron resultados para: $query"
                        }
                    } catch (e: Exception) {
                        // Aquí nos mostrará el error real en la pantalla del móvil
                        statusText.text = "Error Técnico: ${e.localizedMessage}"
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
