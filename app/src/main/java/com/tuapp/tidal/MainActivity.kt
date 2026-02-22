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
    private lateinit var statusText: TextView
    private lateinit var songInfo: TextView
    private lateinit var albumArt: ImageView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var loader: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // CONTENEDOR PRINCIPAL OLED
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(60, 60, 60, 60)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // BUSCADOR MINIMALISTA
        val searchBox = EditText(this).apply {
            hint = "Buscar música HQ..."
            setHintTextColor(Color.DKGRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            setPadding(40, 40, 40, 40)
            textSize = 14f
        }

        val btnSearch = Button(this).apply {
            text = "BUSCAR"
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.WHITE)
            textSize = 12f
        }

        // PORTADA (AL CENTRO)
        albumArt = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(900, 900).apply {
                setMargins(0, 100, 0, 80)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
        }

        // INFO DE CANCIÓN
        songInfo = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 10)
        }

        statusText = TextView(this).apply {
            text = "320kbps Lossy-High"
            setTextColor(Color.parseColor("#1DB954"))
            textSize = 10f
            setPadding(0, 0, 0, 100)
        }

        // CONTROLES DE REPRODUCCIÓN
        val controlsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        btnPlayPause = ImageButton(this).apply {
            // Usamos iconos básicos de Android
            setImageResource(android.R.drawable.ic_media_play)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            scaleX = 2f
            scaleY = 2f
        }

        controlsLayout.addView(btnPlayPause)
        loader = ProgressBar(this).apply { visibility = View.GONE }

        // AÑADIR TODO AL ROOT
        root.addView(searchBox)
        root.addView(btnSearch)
        root.addView(albumArt)
        root.addView(loader)
        root.addView(songInfo)
        root.addView(statusText)
        root.addView(controlsLayout)

        setContentView(root)

        setupPlayer()

        btnSearch.setOnClickListener {
            val q = searchBox.text.toString()
            if (q.isNotEmpty()) searchSong(q)
        }

        btnPlayPause.setOnClickListener {
            player?.let {
                if (it.isPlaying) it.pause() else it.play()
            }
        }
    }

    private fun setupPlayer() {
        player = ExoPlayer.Builder(this).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    btnPlayPause.setImageResource(
                        if (isPlaying) android.R.drawable.ic_media_pause 
                        else android.R.drawable.ic_media_play
                    )
                }
            })
        }
    }

    private fun searchSong(query: String) {
        loader.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val response = URL("https://saavn.me/search/songs?query=$encoded&limit=1").readText()
                val json = JSONObject(response)
                val track = json.getJSONObject("data").getJSONArray("results").getJSONObject(0)

                val name = track.getString("name")
                val artist = track.getJSONObject("primaryArtists").getString("name")
                val img = track.getJSONArray("image").getJSONObject(2).getString("link")
                val url = track.getJSONArray("downloadUrl").getJSONObject(4).getString("link") // 320kbps

                withContext(Dispatchers.Main) {
                    loader.visibility = View.GONE
                    albumArt.visibility = View.VISIBLE
                    albumArt.load(img) {
                        transformations(RoundedCornersTransformation(40f))
                    }
                    songInfo.text = "$name\n$artist"
                    play(url)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loader.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "Error en HQ", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun play(url: String) {
        player?.setMediaItem(MediaItem.fromUri(url))
        player?.prepare()
        player?.play()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
    }
}ayer?.release()
    }
}
