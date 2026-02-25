package com.tuapp.tidal

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private lateinit var seekBar: SeekBar
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        System.setProperty("https.protocols", "TLSv1.2,TLSv1.3")

        // Fondo Negro Profundo
        val root = RelativeLayout(this).apply { setBackgroundColor(Color.BLACK) }

        // 1. Buscador Minimalista
        val searchBox = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(60, 100, 60, 40)
            layoutParams = RelativeLayout.LayoutParams(-1, -2)
        }

        val input = EditText(this).apply {
            hint = "Escribe una canción..."
            setHintTextColor(Color.parseColor("#333333"))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            textSize = 14f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }

        val searchIcon = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_search)
            setColorFilter(Color.GRAY)
            setBackgroundColor(Color.TRANSPARENT)
        }
        searchBox.addView(input)
        searchBox.addView(searchIcon)

        // 2. Arte del Álbum (Studio Style)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val lp = RelativeLayout.LayoutParams(-1, -1)
            lp.addRule(RelativeLayout.BELOW, searchBox.id)
            lp.addRule(RelativeLayout.ABOVE, 3000)
            layoutParams = lp
        }

        albumArt = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(850, 850)
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.INVISIBLE
        }

        songTitle = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(50, 60, 50, 5)
            gravity = Gravity.CENTER
        }

        artistName = TextView(this).apply {
            setTextColor(Color.GRAY)
            textSize = 12f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            letterSpacing = 0.2f
            gravity = Gravity.CENTER
        }

        loader = ProgressBar(this).apply { 
            visibility = View.GONE 
            indeterminateDrawable.setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN)
        }

        content.addView(albumArt)
        content.addView(loader)
        content.addView(songTitle)
        content.addView(artistName)

        // 3. Controles y SeekBar (Abajo)
        val footer = LinearLayout(this).apply {
            id = 3000
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(80, 0, 80, 150)
            val lp = RelativeLayout.LayoutParams(-1, -2)
            lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            layoutParams = lp
        }

        seekBar = SeekBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2)
            progressDrawable.setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN)
            thumb.setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN)
            setPadding(0, 40, 0, 40)
        }

        btnPlayPause = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_play)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            scaleX = 1.8f
            scaleY = 1.8f
        }

        statusText = TextView(this).apply {
            setTextColor(Color.parseColor("#111111"))
            textSize = 8f
            setPadding(0, 30, 0, 0)
        }

        footer.addView(seekBar)
        footer.addView(btnPlayPause)
        footer.addView(statusText)

        root.apply { addView(searchBox); addView(content); addView(footer) }
        setContentView(root)
        setupPlayer()

        searchIcon.setOnClickListener {
            val q = input.text.toString()
            if (q.isNotEmpty()) searchMusicYT(q)
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
                    if (isPlaying) updateProgress()
                }
            })
        }
        
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) player?.seekTo(p.toLong())
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    private fun updateProgress() {
        player?.let {
            seekBar.max = it.duration.toInt()
            seekBar.progress = it.currentPosition.toInt()
            if (it.isPlaying) handler.postDelayed({ updateProgress() }, 1000)
        }
    }

    private fun searchMusicYT(query: String) {
        loader.visibility = View.VISIBLE
        statusText.text = "SYNCING ENGINE..."
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val q = URLEncoder.encode(query, "UTF-8")
                // CAMBIAMOS A UNA INSTANCIA MÁS RÁPIDA (Piped.video es muy estable)
                val searchUrl = URL("https://pipedapi.ducks.party/search?q=$q&filter=music_songs")
                val conn = searchUrl.openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")

                if (conn.responseCode == 200) {
                    val json = JSONObject(conn.inputStream.bufferedReader().readText())
                    val items = json.getJSONArray("items")
                    
                    if (items.length() > 0) {
                        val track = items.getJSONObject(0)
                        val vId = track.getString("url").split("v=")[1]
                        val title = track.getString("title")
                        val artist = track.getString("uploaderName")
                        val cover = track.getString("thumbnail")

                        // Obtener stream directo
                        val sUrl = URL("https://pipedapi.ducks.party/streams/$vId")
                        val sConn = sUrl.openConnection() as HttpURLConnection
                        val sJson = JSONObject(sConn.inputStream.bufferedReader().readText())
                        val audio = sJson.getJSONArray("audioStreams").getJSONObject(0).getString("url")

                        withContext(Dispatchers.Main) {
                            loader.visibility = View.GONE
                            albumArt.visibility = View.VISIBLE
                            albumArt.load(cover)
                            songTitle.text = title
                            artistName.text = artist.uppercase()
                            statusText.text = "ENGINE: $vId"
                            
                            player?.setMediaItem(MediaItem.fromUri(audio))
                            player?.prepare()
                            player?.play()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loader.visibility = View.GONE
                    statusText.text = "TIMEOUT - REINTENTA"
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        handler.removeCallbacksAndMessages(null)
    }
}
