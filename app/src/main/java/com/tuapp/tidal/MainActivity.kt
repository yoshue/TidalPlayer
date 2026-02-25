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
        
        // Protocolos de seguridad reforzados para evitar bloqueos de red
        System.setProperty("https.protocols", "TLSv1.2,TLSv1.3")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(60, 80, 60, 60)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val searchBar = EditText(this).apply {
            hint = "Escribe canción y artista..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(40, 40, 40, 40)
        }

        val btnSearch = Button(this).apply {
            text = "REPRODUCIR (SERVIDOR 2026)"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#00E5FF"))
        }

        albumArt = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(800, 800).apply { setMargins(0, 50, 0, 30) }
            visibility = View.GONE
        }

        songInfo = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
        }

        statusText = TextView(this).apply {
            text = "Estado: Conexión Segura"
            setTextColor(Color.CYAN)
            textSize = 11f
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
            if (q.isNotEmpty()) fetchMusicAntiBlock(q)
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

    private fun fetchMusicAntiBlock(query: String) {
        loader.visibility = View.VISIBLE
        statusText.text = "Saltando bloqueos de red..."
        
        lifecycleScope.launch(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                
                // USAMOS UN ESPEJO DISTINTO QUE NO USA VERCEL
                val url = URL("https://saavn.me/api/search/songs?query=$encoded")
                
                connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    connectTimeout = 15000
                    readTimeout = 15000
                    // IMPORTANTE: User-Agent de navegador real para que no nos detecten como bot
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    setRequestProperty("Accept", "application/json")
                }

                if (connection.responseCode == 200) {
                    val text = connection.inputStream.bufferedReader().readText()
                    
                    if (!text.trim().startsWith("{")) throw Exception("Servidor saturado. Intenta en 10 segundos.")

                    val json = JSONObject(text)
                    val data = json.optJSONObject("data")
                    val results = data?.optJSONArray("results") ?: json.optJSONArray("data")

                    if (results != null && results.length() > 0) {
                        val track = results.getJSONObject(0)
                        val title = track.getString("name").replace("&quot;", "\"")
                        
                        // Extracción de artista compatible con múltiples versiones de API
                        val artist = try {
                            track.getJSONObject("artists").getJSONArray("primary").getJSONObject(0).getString("name")
                        } catch (e: Exception) { "Artista" }
                        
                        val dUrl = track.getJSONArray("downloadUrl")
                        val streamUrl = dUrl.getJSONObject(dUrl.length() - 1).getString("url")
                        
                        val img = track.getJSONArray("image")
                        val cover = img.getJSONObject(img.length() - 1).getString("url")

                        withContext(Dispatchers.Main) {
                            loader.visibility = View.GONE
                            albumArt.visibility = View.VISIBLE
                            albumArt.load(cover) { transformations(RoundedCornersTransformation(40f)) }
                            songInfo.text = "$title\n$artist"
                            statusText.text = "¡CONECTADO! Reproduciendo HQ"
                            
                            player?.setMediaItem(MediaItem.fromUri(streamUrl))
                            player?.prepare()
                            player?.play()
                        }
                    } else {
                        throw Exception("No se encontró la canción.")
                    }
                } else {
                    throw Exception("Error de conexión: ${connection.responseCode}")
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
