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
    
    // Configuración de la API con la URL base limpia
    private val apiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://tidal.squid.wtf/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MusicApiService::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Diseño de la interfaz por código
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        val searchInput = EditText(this).apply { 
            hint = "Nombre de canción o artista..." 
            setSingleLine(true)
        }
        
        val searchButton = Button(this).apply { 
            text = "BUSCAR Y REPRODUCIR" 
        }
        
        val statusText = TextView(this).apply { 
            text = "Estado: Esperando búsqueda"
            textSize = 16f
        }

        layout.addView(searchInput)
        layout.addView(searchButton)
        layout.addView(statusText)
        setContentView(layout)

        // Inicializamos el reproductor
        player = ExoPlayer.Builder(this).build()

        searchButton.setOnClickListener {
            val query = searchInput.text.toString()
            if (query.isNotEmpty()) {
                statusText.text = "Buscando en los servidores de Tidal..."
                
                lifecycleScope.launch {
                    try {
                        // Llamada a la API
                        val response = apiService.searchTracks(query)
                        val tracks = response.items

                        if (tracks.isNotEmpty()) {
                            val firstTrack = tracks[0]
                            
                            // Actualizamos la interfaz con la info encontrada
                            statusText.text = "Encontradas ${tracks.size} canciones.\n" +
                                            "Reproduciendo ahora: ${firstTrack.title}\n" +
                                            "Artista: ${firstTrack.artist.name}"
                            
                            // Construimos la URL de descarga/stream
                            // Usamos el ID de la canción obtenido de la búsqueda
                            val streamUrl = "https://clm-6.tidal.squid.wtf/api/download?id=${firstTrack.id}&
