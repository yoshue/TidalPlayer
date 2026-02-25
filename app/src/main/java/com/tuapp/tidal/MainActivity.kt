package com.tuapp.tidal

import android.graphics.Color
import android.os.Bundle
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
import java.net.URL
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var albumArt: ImageView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var songInfo: TextView
    private lateinit var loader: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // DISEÑO OLED MINIMALISTA
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(50, 80, 50, 50)
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }

        val searchBar = EditText(this).apply {
            hint = "Buscar artista o canción..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#111111"))
            setPadding(40, 40, 40, 40)
        }

        val btnSearch = Button(this).apply {
            text = "BUSCAR HQ"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
        }

        albumArt = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(800, 800).apply { setMargins(0, 50, 0, 50) }
            visibility = View.GONE
        }

        songInfo = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = android.view.Gravity.CENTER
        }

        loader = ProgressBar(this).apply { visibility = View.GONE }

        btnPlayPause = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_play)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            scaleX = 2f
            scaleY = 2f
        }

        root.addView(searchBar)
        root.addView(btnSearch)
        root.addView(albumArt)
        root.addView(loader)
        root.addView(songInfo)
        root.addView(btnPlayPause)

        setContentView(root)
        
        player = ExoPlayer.Builder(this).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    btnPlayPause.setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
                }
            })
        }

        btnSearch.setOnClickListener {
            val q = searchBar.text.toString()
            if (q.isNotEmpty()) performSearch(q)
        }

        btnPlayPause.setOnClickListener {
            player?.let { if (it.isPlaying) it.pause() else it.play() }
        }
    }

    private fun performSearch(query: String) {
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
                val url = track.getJSONArray("downloadUrl").getJSONObject(4).getString("link")

                withContext(Dispatchers.Main) {
                    loader.visibility = View.GONE
                    albumArt.visibility = View.VISIBLE
                    albumArt.load(img) { transformations(RoundedCornersTransformation(30f)) }
                    songInfo.text = "$name\n$artist"
                    player?.setMediaItem(MediaItem.fromUri(url))
                    player?.prepare()
                    player?.play()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loader.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "Error de búsqueda", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
    }
}
