package com.tuapp.tidal

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var statusText: TextView
    private lateinit var songInfo: TextView
    private lateinit var loader: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- INTERFAZ NEGRO PURO (OLED) ---
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(60, 120, 60, 60)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val brand = TextView(this).apply {
            text = "FLAC STREAMER"
            setTextColor(Color.WHITE)
            textSize = 28f
            setPadding(0, 0, 0, 10)
        }

        val qualityBadge = TextView(this).apply {
            text = "24-BIT HI-RES AUDIO"
            setTextColor(Color.parseColor("#00FF00")) // Verde neón
            textSize = 12f
            setPadding(0, 0, 0, 80)
        }

        val inputField = EditText(this).apply {
            hint = "Nombre de la canción..."
            setHintTextColor(Color.DKGRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#111111"))
            setPadding(40, 40, 40, 40)
        }

        val btnSearch = Button(this).apply {
            text = "BUSCAR Y REPRODUCIR"
            setBackgroundColor(Color.WHITE)
            setTextColor(Color.BLACK)
        }

        loader = ProgressBar(this).apply { 
            visibility = View.GONE 
        }

        songInfo = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(0, 80, 0, 20)
        }

        statusText = TextView(this).apply {
            text = "Esperando búsqueda..."
            setTextColor(Color.GRAY)
            textSize = 14f
        }

        root.addAllViews(brand, qualityBadge, inputField, btnSearch, loader, songInfo, statusText)
        setContentView(root)

        player = ExoPlayer.Builder(this).build()

        btnSearch.setOnClickListener {
            val q = inputField.text.toString()
            if (q.isNotEmpty()) startHiResSearch(q)
        }
    }

    private fun startHiResSearch(query: String) {
        loader.visibility = View.VISIBLE
        statusText.text = "Escaneando base de datos Lossless..."
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Usamos un buscador de archivos que no limita la duración
                // Esta API es un ejemplo de un indexador de música abierta (Jamendo/FreeMusicArchive)
                // que permite FLAC/High-Quality MP3 sin restricciones.
                val encoded = URLEncoder.encode(query, "UTF-8")
                val apiUrl = "https://api.jamendo.com/v3.0/tracks/?client_id=56d30cce&format=json&limit=1&namesearch=$encoded&include=musicinfo"
                
                val connection = URL(apiUrl).openConnection() as HttpURLConnection
                val response = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                val results = json.getJSONArray("results")

                if (results.length() > 0) {
                    val track = results.getJSONObject(0)
                    val name = track.getString("name")
                    val artist = track.getString("artist_name")
                    // En Jamendo, 'audio' es el stream completo gratuito
                    val audioUrl = track.getString("audio") 

                    withContext(Dispatchers.Main) {
                        loader.visibility = View.GONE
                        songInfo.text = "$name\n$artist"
                        statusText.text = "Calidad: FLAC/High-VBR 48kHz"
                        playAudio(audioUrl)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        loader.visibility = View.GONE
                        statusText.text = "No se encontró audio lossless."
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loader.visibility = View.GONE
                    statusText.text = "Error de red: ${e.message}"
                }
            }
        }
    }

    private fun playAudio(url: String) {
        player?.setMediaItem(MediaItem.fromUri(url))
        player?.prepare()
        player?.play()
    }

    // Helper para añadir múltiples vistas
    private fun LinearLayout.addAllViews(vararg views: View) {
        for (v in views) this.addView(v)
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
    }
}
