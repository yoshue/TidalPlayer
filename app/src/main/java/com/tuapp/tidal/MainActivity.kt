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
    private lateinit var sourceSpinner: Spinner
    
    // Lista de servidores activos a Feb 2026
    private val serverNames = arrayOf("Servidor Alpha (Principal)", "Servidor Beta (Estable)", "Servidor Gamma (Alternativo)")
    private val serverUrls = arrayOf(
        "https://saavn.dev/api/search/songs?query=",
        "https://saavn.me/search/songs?query=",
        "https://jiosaavn-api.vercel.app/search/songs?query="
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        System.setProperty("https.protocols", "TLSv1.2,TLSv1.3")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(60, 40, 60, 60)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Selector de Fuente
        val label = TextView(this).apply {
            text = "SELECCIONAR FUENTE:"
            setTextColor(Color.GRAY)
            textSize = 12f
        }
        
        sourceSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, serverNames)
            setBackgroundColor(Color.DKGRAY)
        }

        val searchBar = EditText(this).apply {
            hint = "Canción o Artista..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(40, 40, 40, 40)
        }

        val btnSearch = Button(this).apply {
            text = "REPRODUCIR AHORA"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#00E5FF"))
        }

        albumArt = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(800, 800).apply { setMargins(0, 40, 0, 20) }
            visibility = View.GONE
        }

        songInfo = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
        }

        statusText = TextView(this).apply {
            text = "Estado: En espera"
            setTextColor(Color.CYAN)
            textSize = 11f
        }

        loader = ProgressBar(this).apply { visibility = View.GONE }

        btnPlayPause = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_play)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            scaleX = 2f
            scaleY = 2f
        }

        root.addView(label)
        root.addView(sourceSpinner)
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
            if (q.isNotEmpty()) fetchMusic(q)
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

    private fun fetchMusic(query: String) {
        val serverIndex = sourceSpinner.selectedItemPosition
        loader.visibility = View.VISIBLE
        statusText.text = "Conectando a ${serverNames[serverIndex]}..."
        
        lifecycleScope.launch(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = URL(serverUrls[serverIndex] + encoded)
                
                connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                    setRequestProperty("Accept", "application/json")
                }

                if (connection.responseCode == 200) {
                    val text = connection.inputStream.bufferedReader().readText()
                    
                    if (!text.trim().startsWith("{")) throw Exception("Servidor saturado (envió HTML). Prueba otra fuente.")

                    val json = JSONObject(text)
                    val dataObj = json.optJSONObject("data")
                    val results = dataObj?.optJSONArray("results") ?: json.optJSONArray("data") ?: throw Exception("Formato no reconocido")

                    if (results.length() > 0) {
                        val track = results.getJSONObject(0)
                        val title = track.getString("name").replace("&quot;", "\"")
                        val artist = track.optJSONObject("artists")?.optJSONArray("primary")?.optJSONObject(0)?.optString("name") ?: "Desconocido"
                        
                        val dUrls = track.getJSONArray("downloadUrl")
                        val streamUrl = dUrls.getJSONObject(dUrls.length() - 1).getString("url")
                        
                        val imgs = track.getJSONArray("image")
                        val cover = imgs.getJSONObject(imgs.length() - 1).getString("url")

                        withContext(Dispatchers.Main) {
                            loader.visibility = View.GONE
                            albumArt.visibility = View.VISIBLE
                            albumArt.load(cover) { transformations(RoundedCornersTransformation(40f)) }
                            songInfo.text = "$title\n$artist"
                            statusText.text = "¡CONECTADO! Reproduciendo..."
                            
                            player?.setMediaItem(MediaItem.fromUri(streamUrl))
                            player?.prepare()
                            player?.play()
                        }
                    } else {
                        throw Exception("No hay resultados en esta fuente.")
                    }
                } else {
                    throw Exception("Error de red: ${connection.responseCode}")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loader.visibility = View.GONE
                    statusText.text = "Fallo: ${e.message}"
                    Toast.makeText(this@MainActivity, "Intenta cambiar de servidor en el menú", Toast.LENGTH_SHORT).show()
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
