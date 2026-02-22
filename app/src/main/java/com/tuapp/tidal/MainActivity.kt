package com.tuapp.tidal

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    
    // Configuramos el cliente para que sea un "clon" de la web oficial
    private val httpClient = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request().newBuilder()
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Referer", "https://tidal.squid.wtf/")
            .header("Origin", "https://tidal.squid.wtf")
            .header("x-requested-with", "XMLHttpRequest")
            .build()
        chain.proceed(request)
    }.build()

    private val apiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://tidal.squid.wtf/")
            .client(httpClient)
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

        val searchInput = EditText(this).apply { hint = "Buscar en Tidal Squid..." }
        val searchButton = Button(this).apply { text = "BUSCAR" }
        val statusText = TextView(this).apply { text = "Listo" }

        layout.addView(searchInput)
        layout.addView(searchButton)
        layout.addView(statusText)
        setContentView(layout)

        player = ExoPlayer.Builder(this).build()

        searchButton.setOnClickListener {
            val query = searchInput.text.toString()
            if (query.isNotEmpty()) {
                statusText.text = "Consultando API (Modo Oficial)..."
                lifecycleScope.launch {
                    try {
                        val response = apiService.searchTracks(query)
                        val tracks = response.items
                        
                        if (tracks != null && tracks.isNotEmpty()) {
                            val track = tracks[0]
                            statusText.text = "Reproduciendo: ${track.title}"
                            
                            // La URL de descarga también requiere que "engañemos" al servidor
                            val streamUrl = "https://clm-6.tidal.squid.wtf/api/download?id=${track.id}&quality=LOSSLESS"
                            
                            player?.setMediaItem(MediaItem.fromUri(streamUrl))
                            player?.prepare()
                            player?.play()
                        } else {
                            statusText.text = "Sin resultados."
                        }
                    } catch (e: Exception) {
                        statusText.text = "Error: ${e.localizedMessage}"
                    }
                }
            }
        }
    }
}
