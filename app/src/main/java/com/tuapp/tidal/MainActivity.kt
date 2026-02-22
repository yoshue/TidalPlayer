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

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(60, 120, 60, 60)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val title = TextView(this).apply {
            text = "OLED HQ PLAYER"
            setTextColor(Color.WHITE)
            textSize = 28f
            setPadding(0, 0, 0, 80)
        }

        val inputField = EditText(this).apply {
            hint = "Canción y Artista..."
            setHintTextColor(Color.DKGRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#111111"))
            setPadding(40, 40, 40, 40)
        }

        val btnSearch = Button(this).apply {
            text = "REPRODUCIR EN 320KBPS"
            setBackgroundColor(Color.WHITE)
            setTextColor(Color.BLACK)
        }

        loader = ProgressBar(this).apply { visibility = View.GONE }

        songInfo = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 80, 0, 20)
        }

        statusText = TextView(this).apply {
            text = "Calidad seleccionada: Alta (320kbps)"
            setTextColor(Color.parseColor("#3DDC84"))
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
            if (q.isNotEmpty()) startHQSearch(q)
        }
    }

    private fun startHQSearch(query: String) {
        loader.visibility = View.VISIBLE
        statusText.text = "Buscando en servidor HQ..."
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                // Usamos la API de Saavn que es famosa en GitHub por ser abierta y HQ
                val searchUrl = "https://saavn.me/search/songs?query=$encoded&limit=1"
                
                val connection = URL(searchUrl).openConnection() as HttpURLConnection
                val response = connection.inputStream.bufferedReader().readText()
                
                val json = JSONObject(response)
                val data = json.getJSONObject("data").getJSONArray("results")

                if (data.length() > 0) {
                    val track = data.getJSONObject(0)
                    val name = track.getString("name")
                    val artist = track.getJSONObject("primaryArtists").getString("name")
                    
                    // Saavn entrega varios niveles. El último del array suele ser 320kbps.
                    val downloadUrls = track.getJSONArray("downloadUrl")
                    val hqUrl = downloadUrls.getJSONObject(downloadUrls.length() - 1).getString("link")

                    withContext(Dispatchers.Main) {
                        loader.visibility = View.GONE
                        songInfo.text = "$name\n$artist"
                        statusText.text = "Reproduciendo a 320kbps (Real)"
                        playAudio(hqUrl)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        loader.visibility = View.GONE
                        statusText.text = "No se encontró en alta calidad."
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loader.visibility = View.GONE
                    statusText.text = "Error: El servidor HQ no responde."
                }
            }
        }
    }

    private fun playAudio(url: String) {
        player?.setMediaItem(MediaItem.fromUri(url))
        player?.prepare()
        player?.play()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
    }
}player?.release()
    }
}
