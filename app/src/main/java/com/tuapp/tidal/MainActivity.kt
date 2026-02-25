package com.tuapp.tidal

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.load
import coil.transform.RoundedCornersTransformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var albumArt: ImageView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var songInfo: TextView
    private lateinit var loader: ProgressBar
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mantenemos la protección TLS para Huawei
        System.setProperty("https.protocols", "TLSv1.2,TLSv1.3")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(60, 80, 60, 60)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val searchBar = EditText(this).apply {
            hint = "Buscar en Deezer..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(40, 40, 40, 40)
        }

        val btnSearch = Button(this).apply {
            text = "PROBAR SONIDO"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
        }

        albumArt = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(850, 850).apply { setMargins(0, 60, 0, 40) }
            visibility = View.GONE
        }

        songInfo = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
        }

        statusText = TextView(this).apply {
            text = "API: Deezer Public"
            setTextColor(Color.MAGENTA)
            textSize = 11f
            setPadding(0, 0, 0, 60)
        }

        loader = ProgressBar(this).apply { visibility = View.GONE }

        btnPlayPause = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_play)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            scaleX = 2.5f
            scaleY = 2.5f
        }

        root.addView(searchBar)
        root.addView(btnSearch)
        root.addView(albumArt)
        root.addView(loader)
        root.addView(songInfo)
        root.addView(statusText)
        root.addView(btnPlayPause)

        setContentView(root)
        setupPlayer()

        btnSearch.setOnClickListener {
            val q = searchBar.text.toString()
            if (q.isNotEmpty()) searchOnDeezer(q)
        }

        btnPlayPause.setOnClickListener {
            player?.let { if (it.isPlaying) it.pause() else it.play() }
        }
    }

    private fun setupPlayer() {
        player = ExoPlayer.Builder(this).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    btnPlayPause.setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
                }
            })
        }
    }

    private fun searchOnDeezer(query: String) {
        loader.visibility = View.VISIBLE
        statusText.text = "Consultando Deezer..."
        
        lifecycleScope.launch(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                // URL DE DEEZER: Súper estable y devuelve JSON real
                val url = URL("https://api.deezer.com/search?q=$encoded&limit=1")
                
                connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                    setRequestProperty("Accept", "application/json")
                }

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    val data = json.getJSONArray("data")

                    if (data.length() > 0) {
                        val track = data.getJSONObject(0)
                        val title = track.getString("title")
                        val artist = track.getJSONObject("artist").getString("name")
                        val previewUrl = track.getString("preview") // Audio de alta calidad
                        val coverUrl = track.getJSONObject("album").getString("cover_xl")

                        withContext(Dispatchers.Main) {
                            loader.visibility = View.GONE
                            albumArt.visibility = View.VISIBLE
                            albumArt.load(coverUrl) { transformations(RoundedCornersTransformation(40f)) }
                            songInfo.text = "$title\n$artist"
                            statusText.text = "¡CONECTADO! Reproduciendo..."
                            
                            player?.setMediaItem(MediaItem.fromUri(previewUrl))
                            player?.prepare()
                            player?.play()
                        }
                    } else {
                        throw Exception("No se encontró nada")
                    }
                } else {
                    throw Exception("Error del servidor: ${connection.responseCode}")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loader.visibility = View.GONE
                    statusText.text = "Fallo: ${e.message}"
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
    }
}
