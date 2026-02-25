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

        // Seguridad TLS para evitar bloqueos de operadora
        System.setProperty("https.protocols", "TLSv1.2,TLSv1.3")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(60, 80, 60, 60)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val searchBar = EditText(this).apply {
            hint = "Canción completa (320kbps)..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(40, 40, 40, 40)
        }

        val btnSearch = Button(this).apply {
            text = "BUSCAR HQ"
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
            text = "Calidad: Extreme 320kbps"
            setTextColor(Color.parseColor("#1DB954"))
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
            if (q.isNotEmpty()) searchFullHQ(q)
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

    private fun searchFullHQ(query: String) {
        loader.visibility = View.VISIBLE
        statusText.text = "Obteniendo audio HQ..."
        
        lifecycleScope.launch(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                // Usamos el endpoint global de Saavn
                val url = URL("https://saavn.dev/api/search/songs?query=$encoded&limit=1")
                
                connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    connectTimeout = 15000
                    readTimeout = 15000
                    // ESTO ES LO QUE EVITA EL ERROR DE HTML:
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    setRequestProperty("Accept", "application/json")
                }

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    val data = json.getJSONObject("data")
                    val results = data.getJSONArray("results")

                    if (results.length() > 0) {
                        val track = results.getJSONObject(0)
                        val title = track.getString("name").replace("&quot;", "\"")
                        val artist = track.getJSONObject("artists").getJSONArray("primary").getJSONObject(0).getString("name")
                        
                        // Seleccionamos el índice [4] que es el archivo de 320kbps
                        val downloadUrls = track.getJSONArray("downloadUrl")
                        val hqAudioUrl = downloadUrls.getJSONObject(downloadUrls.length() - 1).getString("url")
                        
                        // Imagen de alta resolución
                        val images = track.getJSONArray("image")
                        val coverUrl = images.getJSONObject(images.length() - 1).getString("url")

                        withContext(Dispatchers.Main) {
                            loader.visibility = View.GONE
                            albumArt.visibility = View.VISIBLE
                            albumArt.load(coverUrl) { transformations(RoundedCornersTransformation(40f)) }
                            songInfo.text = "$title\n$artist"
                            statusText.text = "¡FULL SONG! Reproduciendo a 320kbps"
                            
                            player?.setMediaItem(MediaItem.fromUri(hqAudioUrl))
                            player?.prepare()
                            player?.play()
                        }
                    } else {
                        throw Exception("No se encontró la canción")
                    }
                } else {
                    throw Exception("Servidor saturado (${connection.responseCode})")
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
