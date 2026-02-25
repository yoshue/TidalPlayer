package com.tuapp.tidal

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
    private lateinit var songTitle: TextView
    private lateinit var artistName: TextView
    private lateinit var loader: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var seekBar: SeekBar
    private val handler = Handler(Looper.getMainLooper())

    // LISTA DE MOTORES DE RESPALDO (Los más estables a Feb 2026)
    private val apiEndpoints = arrayOf(
        "https://pipedapi.lunar.icu",
        "https://api.piped.victr.me",
        "https://pipedapi.ducks.party"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        System.setProperty("https.protocols", "TLSv1.2,TLSv1.3")

        // Fondo Negro de Estudio
        val root = RelativeLayout(this).apply { setBackgroundColor(Color.BLACK) }

        // 1. Buscador (Solo una línea sutil)
        val searchLayout = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(60, 110, 60, 40)
            layoutParams = RelativeLayout.LayoutParams(-1, -2)
        }

        val input = EditText(this).apply {
            hint = "Escribe una canción..."
            setHintTextColor(Color.parseColor("#444444"))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            textSize = 15f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }

        val searchIcon = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_search)
            setColorFilter(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
        }
        searchLayout.addView(input)
        searchLayout.addView(searchIcon)

        // 2. Contenedor de la Carátula
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val lp = RelativeLayout.LayoutParams(-1, -1)
            lp.addRule(RelativeLayout.BELOW, searchLayout.id)
            lp.addRule(RelativeLayout.ABOVE, 4000)
            layoutParams = lp
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
            setPadding(60, 60, 60, 10)
            gravity = Gravity.CENTER
        }

        artistName = TextView(this).apply {
            setTextColor(Color.parseColor("#888888"))
            textSize = 13f
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

        // 3. Controles Inferiores (Estética Zen)
        val footer = LinearLayout(this).apply {
            id = 4000
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(80, 0, 80, 160)
            val lp = RelativeLayout.LayoutParams(-1, -2)
            lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            layoutParams = lp
        }

        seekBar = SeekBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2)
            progressDrawable.setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN)
            thumb.setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN)
        }

        btnPlayPause = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_play)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            scaleX = 2f
            scaleY = 2f
        }

        statusText = TextView(this).apply {
            setTextColor(Color.parseColor("#222222")) // Casi invisible
            textSize = 8f
            setPadding(0, 30, 0, 0)
        }

        footer.addView(seekBar)
        footer.addView(btnPlayPause)
        footer.addView(statusText)

        root.addView(searchLayout); root.addView(content); root.addView(footer)
        setContentView(root)
        setupPlayer()

        searchIcon.setOnClickListener {
            val q = input.text.toString()
            if (q.isNotEmpty()) startSearchRotation(q)
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

    private fun startSearchRotation(query: String) {
        loader.visibility = View.VISIBLE
        albumArt.visibility = View.INVISIBLE
        statusText.text = "BUSCANDO EN RED..."
        
        lifecycleScope.launch(Dispatchers.IO) {
            var found = false
            for (base in apiEndpoints) {
                if (found) break
                try {
                    val q = URLEncoder.encode(query, "UTF-8")
                    val searchUrl = URL("$base/search?q=$q&filter=music_songs")
                    val conn = searchUrl.openConnection() as HttpURLConnection
                    conn.readTimeout = 5000
                    conn.connectTimeout = 5000

                    if (conn.responseCode == 200) {
                        val json = JSONObject(conn.inputStream.bufferedReader().readText())
                        val items = json.getJSONArray("items")
                        if (items.length() > 0) {
                            val track = items.getJSONObject(0)
                            val vId = track.getString("url").split("v=")[1]
                            
                            // Obtener audio
                            val sUrl = URL("$base/streams/$vId")
                            val sJson = JSONObject(sUrl.readText())
                            val audio = sJson.getJSONArray("audioStreams").getJSONObject(0).getString("url")
                            
                            withContext(Dispatchers.Main) {
                                loader.visibility = View.GONE
                                albumArt.visibility = View.VISIBLE
                                albumArt.load(track.getString("thumbnail")) { transformations(RoundedCornersTransformation(10f)) }
                                songTitle.text = track.getString("title")
                                artistName.text = track.getString("uploaderName").uppercase()
                                player?.setMediaItem(MediaItem.fromUri(audio))
                                player?.prepare(); player?.play()
                                found = true
                            }
                        }
                    }
                } catch (e: Exception) { continue }
            }
            if (!found) {
                withContext(Dispatchers.Main) {
                    loader.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "Red saturada. Prueba otra búsqueda.", Toast.LENGTH_SHORT).show()
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
