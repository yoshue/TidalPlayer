package com.tuapp.tidal

import android.graphics.Color
import android.graphics.Typeface
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
import coil.transform.BlurTransformation
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
    private lateinit var backgroundImage: ImageView
    private lateinit var albumArt: ImageView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var songTitle: TextView
    private lateinit var artistName: TextView
    private lateinit var loader: ProgressBar
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Protocolos de red para evitar el "Connection closed"
        System.setProperty("https.protocols", "TLSv1.2,TLSv1.3")

        val root = RelativeLayout(this).apply { setBackgroundColor(Color.BLACK) }

        // 1. Fondo Blur
        backgroundImage = ImageView(this).apply {
            layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.35f 
        }

        // 2. Overlay Oscuro
        val overlay = View(this).apply {
            layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.argb(190, 0, 0, 0))
        }

        // 3. Buscador Superior
        val searchBox = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(60, 90, 60, 40)
            layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val searchInput = EditText(this).apply {
            hint = "Buscar en YouTube Music..."
            setHintTextColor(Color.parseColor("#444444"))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            textSize = 15f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val searchIcon = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_search)
            setColorFilter(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
        }
        searchBox.addView(searchInput)
        searchBox.addView(searchIcon)

        // 4. Arte y Textos
        val mainUI = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val params = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            params.addRule(RelativeLayout.BELOW, searchBox.id)
            params.addRule(RelativeLayout.ABOVE, 1005)
            layoutParams = params
        }

        albumArt = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(820, 820)
            visibility = View.INVISIBLE
        }

        songTitle = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 21f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(50, 60, 50, 5)
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

        mainUI.addView(albumArt)
        mainUI.addView(loader)
        mainUI.addView(songTitle)
        mainUI.addView(artistName)

        // 5. Controles
        val footer = LinearLayout(this).apply {
            id = 1005
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 140)
            val params = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            layoutParams = params
        }

        btnPlayPause = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_play)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            scaleX = 2.2f
            scaleY = 2.2f
        }

        statusText = TextView(this).apply {
            setTextColor(Color.parseColor("#444444"))
            textSize = 9f
            setPadding(0, 40, 0, 0)
        }

        footer.addView(btnPlayPause)
        footer.addView(statusText)

        root.apply {
            addView(backgroundImage)
            addView(overlay)
            addView(searchBox)
            addView(mainUI)
            addView(footer)
        }

        setContentView(root)
        setupPlayer()

        searchIcon.setOnClickListener {
            val q = searchInput.text.toString()
            if (q.isNotEmpty()) fetchFromYouTube(q)
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

    private fun fetchFromYouTube(query: String) {
        loader.visibility = View.VISIBLE
        statusText.text = "BUSCANDO EN YOUTUBE ENGINE..."
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                // MOTOR DE YOUTUBE (Instancia Invidious/Piped)
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
                        val uploader = track.getString("uploaderName")
                        val thumbnail = track.getString("thumbnail")

                        // URL de audio directa de YouTube
                        val streamUrl = "https://pipedapi.kavin.rocks/streams/$videoId"
                        val streamConn = URL(streamUrl).openConnection() as HttpURLConnection
                        val streamJson = JSONObject(streamConn.inputStream.bufferedReader().readText())
                        val audioUrl = streamJson.getJSONArray("audioStreams").getJSONObject(0).getString("url")

                        withContext(Dispatchers.Main) {
                            loader.visibility = View.GONE
                            albumArt.visibility = View.VISIBLE
                            
                            backgroundImage.load(thumbnail) { transformations(BlurTransformation(this@MainActivity, 25, 3)) }
                            albumArt.load(thumbnail) { transformations(RoundedCornersTransformation(30f)) }
                            
                            songTitle.text = title
                            artistName.text = uploader.uppercase()
                            statusText.text = "YT ENGINE: ACTIVE STREAM"
                            
                            player?.setMediaItem(MediaItem.fromUri(audioUrl))
                            player?.prepare()
                            player?.play()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loader.visibility = View.GONE
                    statusText.text = "ERROR: PRUEBA OTRA BÚSQUEDA"
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
    }
}
