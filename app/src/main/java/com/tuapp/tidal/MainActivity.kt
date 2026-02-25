package com.tuapp.tidal

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.load
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
    private lateinit var songTitle: TextView
    private lateinit var artistName: TextView
    private lateinit var loader: ProgressBar
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Protocolo TLS para evitar cierres de conexión inesperados
        System.setProperty("https.protocols", "TLSv1.2,TLSv1.3")

        // Fondo: Negro puro para el look minimalista
        val root = RelativeLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        // 1. Buscador sutil (solo una línea y una lupa)
        val searchHeader = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(60, 100, 60, 40)
            layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val searchInput = EditText(this).apply {
            hint = "Escribe canción o artista..."
            setHintTextColor(Color.parseColor("#252525"))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            textSize = 14f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val searchBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_search)
            setColorFilter(Color.GRAY)
            setBackgroundColor(Color.TRANSPARENT)
        }
        searchHeader.addView(searchInput)
        searchHeader.addView(searchBtn)

        // 2. Contenedor de la Obra (Arte y Texto)
        val mainContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val params = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            params.addRule(RelativeLayout.BELOW, searchHeader.id)
            params.addRule(RelativeLayout.ABOVE, 2000)
            layoutParams = params
        }

        albumArt = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(850, 850)
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.INVISIBLE
        }

        songTitle = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(40, 60, 40, 10)
            gravity = Gravity.CENTER
        }

        artistName = TextView(this).apply {
            setTextColor(Color.GRAY)
            textSize = 13f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            letterSpacing = 0.2f
            gravity = Gravity.CENTER
        }

        loader = ProgressBar(this).apply { 
            visibility = View.GONE 
            indeterminateDrawable.setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN)
        }

        mainContent.addView(albumArt)
        mainContent.addView(loader)
        mainContent.addView(songTitle)
        mainContent.addView(artistName)

        // 3. Controles (Abajo)
        val controlsArea = LinearLayout(this).apply {
            id = 2000
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 150)
            val params = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            layoutParams = params
        }

        btnPlayPause = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_play)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            scaleX = 2.0f
            scaleY = 2.0f
        }

        statusText = TextView(this).apply {
            setTextColor(Color.parseColor("#151515")) // Casi invisible para no romper el minimalismo
            textSize = 8f
            setPadding(0, 40, 0, 0)
        }

        controlsArea.addView(btnPlayPause)
        controlsArea.addView(statusText)

        root.addView(searchHeader)
        root.addView(mainContent)
        root.addView(controlsArea)

        setContentView(root)
        setupPlayer()

        searchBtn.setOnClickListener {
            val q = searchInput.text.toString()
            if (q.isNotEmpty()) fetchFromYouTubeEngine(q)
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

    private fun fetchFromYouTubeEngine(query: String) {
        loader.visibility = View.VISIBLE
        statusText.text = "BUSCANDO..."
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                // Usamos un proxy de YouTube Music ultra-estable
                val searchUrl = URL("https://pipedapi.kavin.rocks/search?q=$encoded&filter=music_songs")
                val conn = searchUrl.openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")

                if (conn.responseCode == 200) {
                    val json = JSONObject(conn.inputStream.bufferedReader().readText())
                    val items = json.getJSONArray("items")
                    
                    if (items.length() > 0) {
                        val track = items.getJSONObject(0)
                        val videoId = track.getString("url").split("v=")[1]
                        val title = track.getString("title")
                        val artist = track.getString("uploaderName")
                        val cover = track.getString("thumbnail")

                        // Segunda llamada para extraer el audio real (streaming)
                        val streamUrl = "https://pipedapi.kavin.rocks/streams/$videoId"
                        val streamConn = URL(streamUrl).openConnection() as HttpURLConnection
                        val streamJson = JSONObject(streamConn.inputStream.bufferedReader().readText())
                        val audioLink = streamJson.getJSONArray("audioStreams").getJSONObject(0).getString("url")

                        withContext(Dispatchers.Main) {
                            loader.visibility = View.GONE
                            albumArt.visibility = View.VISIBLE
                            albumArt.load(cover)
                            songTitle.text = title
                            artistName.text = artist.uppercase()
                            statusText.text = "CONNECTED"
                            
                            player?.setMediaItem(MediaItem.fromUri(audioLink))
                            player?.prepare()
                            player?.play()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loader.visibility = View.GONE
                    statusText.text = "ERROR DE RED"
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
    }
}
