package com.tuapp.tidal

import android.graphics.*
import android.media.audiofx.DynamicsProcessing
import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.*
import coil.load
import coil.transform.BlurTransformation
import coil.transform.RoundedCornersTransformation
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL

// Modelos de datos mejorados
data class Song(val id: String, val title: String, val artist: String, val artistId: String, val albumId: String, val cover: String, val preview: String, val duration: Int = 0)
data class Album(val id: String, val title: String, val cover: String, val artist: String)
data class LyricLine(val timeMs: Long, val text: String)

class MainActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var songAdapter: SongAdapter
    private lateinit var albumAdapter: AlbumAdapter
    private val songList = mutableListOf<Song>()
    private val albumList = mutableListOf<Album>()
    
    // UI Elements
    private lateinit var miniSeekBar: SeekBar
    private lateinit var btnPlayMini: ImageButton
    private lateinit var miniPlayer: CardView
    private lateinit var miniTitle: TextView
    private var currentSong: Song? = null
    private var fullSeekBar: SeekBar? = null
    private var timeElapsedTxt: TextView? = null
    private var timeTotalTxt: TextView? = null

    // Audio Engine (AutoEq)
    private var dynamicsProcessing: DynamicsProcessing? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = RelativeLayout(this).apply { setBackgroundColor(Color.BLACK) }

        // --- CABECERA Y BUSCADOR ---
        val header = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.VERTICAL
            setPadding(50, 100, 50, 40)
        }
        val titleMain = TextView(this).apply { text = "Explorar"; setTextColor(Color.WHITE); textSize = 34f; typeface = Typeface.DEFAULT_BOLD }
        val searchBox = RelativeLayout(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.parseColor("#121212")); cornerRadius = 30f }
            setPadding(40, 15, 40, 15); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 40 }
        }
        val input = EditText(this).apply {
            id = View.generateViewId(); hint = "Artistas o canciones..."; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE); background = null
            imeOptions = EditorInfo.IME_ACTION_SEARCH; inputType = android.text.InputType.TYPE_CLASS_TEXT
            layoutParams = RelativeLayout.LayoutParams(-1, -2).apply { addRule(RelativeLayout.START_OF, 99) }
        }
        val searchIcon = ImageView(this).apply {
            id = 99; setImageResource(android.R.drawable.ic_menu_search); setColorFilter(Color.GRAY)
            layoutParams = RelativeLayout.LayoutParams(70, 70).apply { addRule(RelativeLayout.ALIGN_PARENT_END); addRule(RelativeLayout.CENTER_VERTICAL) }
            setOnClickListener { searchEverything(input.text.toString()) }
        }
        input.setOnEditorActionListener { v, actionId, _ -> if (actionId == EditorInfo.IME_ACTION_SEARCH) { searchEverything(v.text.toString()); true } else false }
        searchBox.addView(input); searchBox.addView(searchIcon); header.addView(titleMain); header.addView(searchBox)

        // --- LISTAS ---
        val scroll = androidx.core.widget.NestedScrollView(this).apply {
            layoutParams = RelativeLayout.LayoutParams(-1, -1).apply { addRule(RelativeLayout.BELOW, header.id) }
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(50, 0, 50, 400) }
        
        content.addView(createLabel("Álbumes Destacados"))
        val rvAlbums = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false) }
        albumAdapter = AlbumAdapter(albumList) { openAlbumDetail(it) }
        rvAlbums.adapter = albumAdapter
        content.addView(rvAlbums)

        content.addView(createLabel("Novedades"))
        val rvSongs = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@MainActivity); isNestedScrollingEnabled = false }
        songAdapter = SongAdapter(songList) { playSong(it) }
        rvSongs.adapter = songAdapter
        content.addView(rvSongs)

        scroll.addView(content)
        val miniP = createMiniPlayer()
        root.addView(header); root.addView(scroll); root.addView(miniP)
        setContentView(root)

        setupPlayer(); searchEverything("Megadeth"); loadOfflineAutoEq()
    }

    // --- REPRODUCTOR EXPANDIDO (GIRO 3D, LADO B, TIEMPOS) ---
    private fun openFullPlayer() {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val view = RelativeLayout(this).apply { setBackgroundColor(Color.BLACK) }

        // Fondo Adaptativo
        val blurBg = ImageView(this).apply {
            layoutParams = RelativeLayout.LayoutParams(-1, -1)
            scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0.4f
            load(currentSong?.cover) { transformations(BlurTransformation(this@MainActivity, 20f)) }
        }

        val contentLayout = RelativeLayout(this).apply { setPadding(60, 80, 60, 80) }

        // BOTONES SUPERIORES
        val btnClose = ImageButton(this).apply { setImageResource(android.R.drawable.ic_menu_close_clear_cancel); setBackgroundColor(0); setColorFilter(Color.WHITE); setOnClickListener { dialog.dismiss() } }
        val btnMenu = ImageButton(this).apply { 
            setImageResource(android.R.drawable.ic_menu_more); setBackgroundColor(0); setColorFilter(Color.WHITE)
            layoutParams = RelativeLayout.LayoutParams(100, 100).apply { addRule(RelativeLayout.ALIGN_PARENT_END) }
            setOnClickListener { showMenuOptions(it, dialog) }
        }

        // CONTENEDOR FLIP 3D
        val flipContainer = FrameLayout(this).apply {
            id = View.generateViewId()
            layoutParams = RelativeLayout.LayoutParams(900, 900).apply { addRule(RelativeLayout.CENTER_IN_PARENT); bottomMargin = 250 }
        }

        val frontCard = CardView(this).apply { radius = 60f; elevation = 30f
            addView(ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP; load(currentSong?.cover) })
        }

        val backCard = CardView(this).apply {
            radius = 60f; visibility = View.GONE; setCardBackgroundColor(Color.parseColor("#121212"))
            val backImg = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP; load(currentSong?.cover) { transformations(BlurTransformation(this@MainActivity, 25f)) } }
            val scrollTracks = androidx.core.widget.NestedScrollView(context)
            val tracklist = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40) }
            
            // Cargar Tracks Lado B
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val res = JSONObject(URL("https://api.deezer.com/album/${currentSong?.albumId}/tracks").readText()).getJSONArray("data")
                    withContext(Dispatchers.Main) {
                        tracklist.addView(TextView(context).apply { text = "SIDE B / TRACKLIST"; setTextColor(Color.CYAN); textSize = 12f; setPadding(0,0,0,30); gravity = Gravity.CENTER })
                        for (i in 0 until res.length()) {
                            val t = res.getJSONObject(i)
                            tracklist.addView(TextView(context).apply { 
                                text = "${i+1}. ${t.getString("title")}"; setTextColor(Color.WHITE); setPadding(0, 15, 0, 15)
                                setOnClickListener { player?.volume = 0f; playSong(currentSong!!); player?.volume = 1f } // Efecto scratch simulado
                            })
                        }
                    }
                } catch(e: Exception) {}
            }
            scrollTracks.addView(tracklist); addView(backImg); addView(scrollTracks)
        }
        flipContainer.addView(backCard); flipContainer.addView(frontCard)
        
        var isFront = true
        flipContainer.setOnClickListener {
            val scale = resources.displayMetrics.density; flipContainer.cameraDistance = 9000 * scale
            val (outV, inV) = if (isFront) frontCard to backCard else backCard to frontCard
            outV.animate().rotationY(90f).setDuration(300).withEndAction {
                outV.visibility = View.GONE; inV.rotationY = -90f; inV.visibility = View.VISIBLE
                inV.animate().rotationY(0f).setDuration(300).start()
            }.start()
            isFront = !isFront
        }

        // INFO Y CONTROLES
        val title = TextView(this).apply { id = View.generateViewId(); text = currentSong?.title; setTextColor(Color.WHITE); textSize = 26f; typeface = Typeface.DEFAULT_BOLD; layoutParams = RelativeLayout.LayoutParams(-1, -2).apply { addRule(RelativeLayout.BELOW, flipContainer.id); topMargin = 40 } }
        val artist = TextView(this).apply { text = currentSong?.artist; setTextColor(Color.GRAY); textSize = 18f; layoutParams = RelativeLayout.LayoutParams(-1, -2).apply { addRule(RelativeLayout.BELOW, title.id) } }

        val timeLayout = RelativeLayout(this).apply { id = View.generateViewId(); layoutParams = RelativeLayout.LayoutParams(-1, -2).apply { addRule(RelativeLayout.BELOW, artist.id); topMargin = 50 } }
        timeElapsedTxt = TextView(this).apply { text = "0:00"; setTextColor(Color.WHITE); textSize = 12f }
        timeTotalTxt = TextView(this).apply { text = "0:00"; setTextColor(Color.WHITE); textSize = 12f; layoutParams = RelativeLayout.LayoutParams(-2, -2).apply { addRule(RelativeLayout.ALIGN_PARENT_END) } }
        fullSeekBar = SeekBar(this).apply { 
            progressDrawable.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN); thumb.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            layoutParams = RelativeLayout.LayoutParams(-1, -2).apply { topMargin = 40 }
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { if(f) player?.seekTo(p.toLong()) }
                override fun onStartTrackingTouch(p0: SeekBar?) {}
                override fun onStopTrackingTouch(p0: SeekBar?) {}
            })
        }
        timeLayout.addView(timeElapsedTxt); timeLayout.addView(timeTotalTxt); timeLayout.addView(fullSeekBar)

        val bPlay = ImageButton(this).apply { 
            setImageResource(if(player?.isPlaying == true) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.WHITE) }
            setColorFilter(Color.BLACK); setPadding(30, 30, 30, 30)
            layoutParams = RelativeLayout.LayoutParams(180, 180).apply { addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); addRule(RelativeLayout.CENTER_HORIZONTAL); bottomMargin = 50 }
            setOnClickListener { if(player?.isPlaying == true) { player?.pause(); setImageResource(android.R.drawable.ic_media_play) } else { player?.play(); setImageResource(android.R.drawable.ic_media_pause) } }
        }

        view.addView(blurBg); contentLayout.addView(btnClose); contentLayout.addView(btnMenu); contentLayout.addView(flipContainer); contentLayout.addView(title); contentLayout.addView(artist); contentLayout.addView(timeLayout);
