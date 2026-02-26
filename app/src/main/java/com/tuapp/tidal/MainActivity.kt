package com.tuapp.tidal

import android.graphics.*
import android.media.audiofx.Equalizer
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
import coil.transform.RoundedCornersTransformation
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL

data class Song(val id: String, val title: String, val artist: String, val cover: String, val preview: String, val albumId: String = "")
data class Album(val id: String, val title: String, val cover: String, val artist: String)

class MainActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private var equalizer: Equalizer? = null
    
    private lateinit var songAdapter: SongAdapter
    private lateinit var albumAdapter: AlbumAdapter
    private val songList = mutableListOf<Song>()
    private val albumList = mutableListOf<Album>()
    
    private lateinit var miniSeekBar: SeekBar
    private lateinit var btnPlayMini: ImageButton
    private lateinit var miniPlayer: CardView
    private lateinit var miniTitle: TextView
    private var currentSong: Song? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = RelativeLayout(this).apply { setBackgroundColor(Color.BLACK) }

        // --- INTERFAZ PRINCIPAL ---
        val mainContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 100, 40, 0)
        }

        val searchContainer = RelativeLayout(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply { 
                setColor(Color.parseColor("#1A1A1A")); cornerRadius = 20f 
            }
            setPadding(30, 10, 30, 10)
        }

        val searchInput = EditText(this).apply {
            id = View.generateViewId()
            hint = "Buscar música..."; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE)
            background = null; imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            layoutParams = RelativeLayout.LayoutParams(-1, -2).apply { addRule(RelativeLayout.START_OF, 99) }
        }

        val searchBtn = ImageView(this).apply {
            id = 99; setImageResource(android.R.drawable.ic_menu_search)
            setColorFilter(Color.WHITE); layoutParams = RelativeLayout.LayoutParams(80, 80).apply { addRule(RelativeLayout.ALIGN_PARENT_END); addRule(RelativeLayout.CENTER_VERTICAL) }
            setOnClickListener { searchEverything(searchInput.text.toString()) }
        }
        searchInput.setOnEditorActionListener { v, id, _ -> if(id == EditorInfo.IME_ACTION_SEARCH) { searchEverything(v.text.toString()); true } else false }
        
        searchContainer.addView(searchInput); searchContainer.addView(searchBtn)
        mainContent.addView(searchContainer)

        // Listas
        val scroll = androidx.core.widget.NestedScrollView(this).apply { layoutParams = RelativeLayout.LayoutParams(-1, -1).apply { setMargins(0, 250, 0, 0) } }
        val scrollContent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, 300) }

        val rvAlbums = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false) }
        albumAdapter = AlbumAdapter(albumList) { openAlbum(it) }
        rvAlbums.adapter = albumAdapter

        val rvSongs = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@MainActivity); isNestedScrollingEnabled = false }
        songAdapter = SongAdapter(songList) { playSong(it) }
        rvSongs.adapter = songAdapter

        scrollContent.addView(createLabel("Álbumes")); scrollContent.addView(rvAlbums)
        scrollContent.addView(createLabel("Canciones")); scrollContent.addView(rvSongs)
        scroll.addView(scrollContent)

        // Mini Player
        val miniP = createMiniPlayer()
        
        root.addView(mainContent); root.addView(scroll); root.addView(miniP)
        setContentView(root)
        setupPlayer(); loadInitialData()
    }

    // --- LÓGICA DE DATOS ---
    private fun loadInitialData() = searchEverything("Top Global")

    private fun searchEverything(q: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val trackJson = JSONObject(URL("https://api.deezer.com/search?q=$q").readText()).getJSONArray("data")
                val albumJson = JSONObject(URL("https://api.deezer.com/search/album?q=$q").readText()).getJSONArray("data")
                withContext(Dispatchers.Main) {
                    songList.clear(); albumList.clear()
                    for(i in 0 until trackJson.length()) {
                        val o = trackJson.getJSONObject(i)
                        songList.add(Song(o.getString("id"), o.getString("title"), o.getJSONObject("artist").getString("name"), o.getJSONObject("album").getString("cover_medium"), o.getString("preview")))
                    }
                    for(i in 0 until albumJson.length()) {
                        val o = albumJson.getJSONObject(i)
                        albumList.add(Album(o.getString("id"), o.getString("title"), o.getString("cover_medium"), o.getJSONObject("artist").getString("name")))
                    }
                    songAdapter.notifyDataSetChanged(); albumAdapter.notifyDataSetChanged()
                }
            } catch(e: Exception) {}
        }
    }

    private fun openAlbum(a: Album) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val res = JSONObject(URL("https://api.deezer.com/album/${a.id}/tracks").readText()).getJSONArray("data")
                withContext(Dispatchers.Main) {
                    songList.clear()
                    for(i in 0 until res.length()) {
                        val o = res.getJSONObject(i)
                        songList.add(Song(o.getString("id"), o.getString("title"), a.artist, a.cover, o.getString("preview")))
                    }
                    songAdapter.notifyDataSetChanged()
                    Toast.makeText(this@MainActivity, "Cargando álbum: ${a.title}", Toast.makeText.LENGTH_SHORT).show()
                }
            } catch(e: Exception) {}
        }
    }

    // --- REPRODUCTOR ---
    private fun setupPlayer() {
        player = ExoPlayer.Builder(this).build()
        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(s: Int) {
                if(s == Player.STATE_READY) {
                    miniSeekBar.max = player?.duration?.toInt() ?: 0
                    updateSeekBars()
                }
            }
        })
    }

    private fun playSong(s: Song) {
        currentSong = s
        miniPlayer.visibility = View.VISIBLE
        miniTitle.text = s.title
        findViewById<ImageView>(777).load(s.cover)
        player?.setMediaItem(MediaItem.fromUri(s.preview))
        player?.prepare(); player?.play()
        btnPlayMini.setImageResource(android.R.drawable.ic_media_pause)
    }

    private var fullSeekBar: SeekBar? = null
    private fun updateSeekBars() {
        lifecycleScope.launch {
            while(isActive) {
                player?.let {
                    miniSeekBar.progress = it.currentPosition.toInt()
                    fullSeekBar?.progress = it.currentPosition.toInt()
                }
                delay(1000)
            }
        }
    }

    // --- PANTALLA EXPANDIDA ---
    private fun openFullPlayer() {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val view = RelativeLayout(this).apply { setBackgroundColor(Color.parseColor("#0A0A0A")); setPadding(60, 80, 60, 60) }

        // Botón Cerrar e Icono Tuerca
        val topBar = RelativeLayout(this).apply { id = View.generateViewId() }
        val btnClose = ImageButton(this).apply { setImageResource(android.R.drawable.ic_menu_close_clear_cancel); setBackgroundColor(0); setColorFilter(Color.WHITE); setOnClickListener { dialog.dismiss() } }
        val btnMenu = ImageButton(this).apply { 
            setImageResource(android.R.drawable.ic_menu_manage); setBackgroundColor(0); setColorFilter(Color.WHITE)
            layoutParams = RelativeLayout.LayoutParams(100,
