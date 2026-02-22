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
import org.json.JSONArray
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

        // DISEÑO OLED NEGRO PURO
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(60, 120, 60, 60)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val title = TextView(this).apply {
            text = "YT SCRAPER OLED"
            setTextColor(Color.WHITE)
            textSize = 28f
            setPadding(0, 0, 0, 80)
        }

        val inputField = EditText(this).apply {
            hint = "Escribe canción y artista..."
            setHintTextColor(Color.DKGRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#111111"))
            setPadding(40, 40, 40, 40)
        }

        val btnSearch = Button(this).apply {
            text = "REPRODUCIR COMPLETA"
            setBackgroundColor(Color.WHITE)
            setTextColor(Color.BLACK)
            setPadding(0, 30, 0, 30)
        }

        loader = ProgressBar(this).apply { visibility = View.GONE }

        songInfo = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 80, 0, 20)
        }

        statusText = TextView(this).apply {
            text = "Listo para scrapear audio"
            setTextColor(Color.GRAY)
            textSize = 12f
        }

        root.addView(title)
        root.addView(inputField)
        root.addView(btnSearch)
        root.addView(loader)
        root.addView(songInfo)
        root.addView(statusText)

        setContentView(root)

        player = ExoPlayer.Builder(this).build()

        btnSearch.setOnClickListener {
            val q = inputField.text.toString()
            if (q.isNotEmpty()) startYoutubeScraper(q)
        }
    }

    private fun startYoutubeScraper(query: String) {
        loader.visibility = View.VISIBLE
        statusText.text = "Buscando en YouTube..."
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Buscamos el video (añadimos "audio" para mejores resultados)
                val encoded = URLEncoder.encode("$query audio", "UTF-8")
                // Instancia pública de Invidious
                val searchUrl = "https://inv.tux.rs/api/v1/search?q=$encoded&type=video"
                
                val connection = URL(searchUrl).openConnection() as HttpURLConnection
                val response = connection.inputStream.bufferedReader().readText()
                val results = JSONArray(response)

                if (results.length() > 0) {
                    val video = results.getJSONObject(0)
                    val vId = video.getString("videoId")
                    val vTitle = video.getString("title")

                    // URL de streaming directo (formato m4a/aac)
                    val streamUrl = "https://inv.tux.rs/latest_version?id=$vId&itag=140"

                    withContext(Dispatchers.Main) {
                        loader.visibility = View.GONE
                        songInfo.text = vTitle
                        statusText.text = "Reproduciendo flujo completo..."
                        playAudio(streamUrl)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        loader.visibility = View.GONE
                        statusText.text = "No se encontraron resultados."
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loader.visibility = View.GONE
                    statusText.text = "Error de conexión con el scraper."
                }
            }
        }
    }

    private fun playAudio(url: String) {
        val mediaItem = MediaItem.fromUri(url)
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.play()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
    }
}
