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

// Modelos
data class Song(val id: String, val title: String, val artist: String, val cover: String, val preview: String, val albumId: String = "")
data class Album(val id: String, val title: String, val cover: String, val artist: String)

class MainActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private var equalizer: Equalizer? = null
    
    private lateinit var songAdapter: SongAdapter
    private lateinit var albumAdapter: AlbumAdapter
    private val songList = mutableListOf<Song>()
    private val albumList = mutableListOf<Album>()
    
    private lateinit var seekBar: SeekBar
    private lateinit var btnPlay: ImageButton
    private lateinit var miniPlayer: CardView
    private lateinit var miniTitle: TextView
    private var currentSong: Song? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val root = RelativeLayout(this).apply { setBackgroundColor(Color.parseColor("#050505")) }
        
        // --- 1. CABECERA Y BUSCADOR ---
        val headerLayout = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.VERTICAL
            setPadding(50, 100, 50, 40)
        }
        
        val titleMain = TextView(this).apply {
            text = "Música"; setTextColor(Color.WHITE); textSize = 34f; typeface = Typeface.DEFAULT_BOLD
        }
        
        val searchContainer = RelativeLayout(this).apply {
            background = createSearchBg()
            setPadding(30, 10, 30, 10)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 40 }
        }
        
        val searchInput = EditText(this).apply {
            id = View.generateViewId()
            hint = "Artistas o canciones"; setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE); background = null
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            layoutParams = RelativeLayout.LayoutParams(-1, -2).apply { addRule(RelativeLayout.START_OF, 999) }
        }
        
        val searchIcon = ImageView(this).apply {
            id = 999
            setImageResource(android.R.drawable.ic_menu_search)
            setColorFilter(Color.WHITE)
            layoutParams = RelativeLayout.LayoutParams(80, 80).apply { addRule(RelativeLayout.ALIGN_PARENT_END); addRule(RelativeLayout.CENTER_VERTICAL) }
            setOnClickListener { searchEverything(searchInput.text.toString()) }
        }
        
        // Evento ENTER del teclado
        searchInput.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchEverything(v.text.toString())
                true
            } else false
        }

        searchContainer.addView(searchInput); searchContainer.addView(searchIcon)
        headerLayout.addView(titleMain); headerLayout.addView(searchContainer)

        // --- 2. CONTENIDO (SCROLL) ---
        val scroll = androidx.core.widget.NestedScrollView(this).apply {
            layoutParams = RelativeLayout.LayoutParams(-1, -1).apply { addRule(RelativeLayout.BELOW, headerLayout.id) }
        }
        
        val content = LinearLayout(this).apply { 
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 350) 
        }

        // Sección Álbumes (Horizontal)
        content.addView(createLabel("Álbumes para ti"))
        val rvAlbums = RecyclerView(this).apply { 
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
        }
        albumAdapter = AlbumAdapter(albumList)
        rvAlbums.adapter = albumAdapter
        content.addView(rvAlbums)

        // Sección Canciones (Vertical)
        content.addView(createLabel("Canciones destacadas"))
        val rvSongs = RecyclerView(this).apply { 
            layoutManager = LinearLayoutManager(this@MainActivity)
            isNestedScrollingEnabled = false 
        }
        songAdapter = SongAdapter(songList) { playSong(it) }
        rvSongs.adapter = songAdapter
        content.addView(rvSongs)

        scroll.addView(content)
        
        // --- 3. MINI PLAYER ---
        val pView = createMiniPlayer()
        
        root.addView(headerLayout); root.addView(scroll); root.addView(pView)
        setContentView(root)

        setupPlayer()
        loadInitialData()
    }

    // --- LÓGICA DE DATOS ---
    private fun loadInitialData() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val res = URL("https://api.deezer.com/chart/0").readText()
                val json = JSONObject(res)
                val tracks = json.getJSONObject("tracks").getJSONArray("data")
                val albums = json.getJSONObject("albums").getJSONArray("data")
                
                withContext(Dispatchers.Main) {
                    songList.clear(); albumList.clear()
                    for (i in 0 until tracks.length()) {
                        val o = tracks.getJSONObject(i)
                        songList.add(Song(o.getString("id"), o.getString("title"), o.getJSONObject("artist").getString("name"), o.getJSONObject("album").getString("cover_medium"), o.getString("preview")))
                    }
                    for (i in 0 until albums.length()) {
                        val o = albums.getJSONObject(i)
                        albumList.add(Album(o.getString("id"), o.getString("title"), o.getString("cover_medium"), o.getJSONObject("artist").getString("name")))
                    }
                    songAdapter.notifyDataSetChanged(); albumAdapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {}
        }
    }

    private fun searchEverything(q: String) {
        if (q.isEmpty()) return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val trackRes = URL("https://api.deezer.com/search?q=$q").readText()
                val albumRes = URL("https://api.deezer.com/search/album?q=$q").readText()
                
                val tracks = JSONObject(trackRes).getJSONArray("data")
                val albums = JSONObject(albumRes).getJSONArray("data")

                withContext(Dispatchers.Main) {
                    songList.clear(); albumList.clear()
                    for (i in 0 until tracks.length()) {
                        val o = tracks.getJSONObject(i)
                        songList.add(Song(o.getString("id"), o.getString("title"), o.getJSONObject("artist").getString("name"), o.getJSONObject("album").getString("cover_medium"), o.getString("preview")))
                    }
                    for (i in 0 until albums.length()) {
                        val o = albums.getJSONObject(i)
                        albumList.add(Album(o.getString("id"), o.getString("title"), o.getString("cover_medium"), o.getJSONObject("artist").getString("name")))
                    }
                    songAdapter.notifyDataSetChanged(); albumAdapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {}
        }
    }

    // --- REPRODUCTOR ---
    private fun setupPlayer() {
        player = ExoPlayer.Builder(this).build()
        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(s: Int) {
                if (s == Player.STATE_READY) {
                    seekBar.max = player?.duration?.toInt() ?: 0
                    updateProgress()
                }
            }
        })
    }

    private fun playSong(s: Song) {
        currentSong = s
        miniPlayer.visibility = View.VISIBLE
        miniTitle.text = s.title
        findViewById<ImageView>(888).load(s.cover)
        
        player?.setMediaItem(MediaItem.fromUri(s.preview))
        player?.prepare(); player?.play()
        btnPlay.setImageResource(android.R.drawable.ic_media_pause)
    }

    private fun updateProgress() {
        lifecycleScope.launch {
            while(true) {
                player?.let { if(it.isPlaying) seekBar.progress = it.currentPosition.toInt() }
                delay(1000)
            }
        }
    }

    // --- INTERFAZ EXPANDIDA (PLAYER FULL) ---
    private fun openFullPlayer() {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            setPadding(60, 100, 60, 60)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val btnClose = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(0); setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(100, 100).apply { gravity = Gravity.START }
            setOnClickListener { dialog.dismiss() }
        }

        val coverFull = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(800, 800).apply { topMargin = 100 }
            load(currentSong?.cover) { transformations(RoundedCornersTransformation(40f)) }
        }

        val titleFull = TextView(this).apply {
            text = currentSong?.title; setTextColor(Color.WHITE); textSize = 24f
            setPadding(0, 60, 0, 10); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        }

        val artistFull = TextView(this).apply {
            text = currentSong?.artist; setTextColor(Color.CYAN); textSize = 18f; gravity = Gravity.CENTER
        }

        // Botón Ecualizador
        val btnEq = Button(this).apply {
            text = "Ecualizador Paramétrico"; setTextColor(Color.BLACK)
            background.setColorFilter(Color.CYAN, PorterDuff.Mode.SRC_IN)
            setOnClickListener {
                val intent = android.content.Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                intent.putExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, player?.audioSessionId)
                intent.putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                startActivityForResult(intent, 999)
            }
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = 100 }
        }

        view.addView(btnClose); view.addView(coverFull); view.addView(titleFull); view.addView(artistFull); view.addView(btnEq)
        dialog.setContentView(view)
        dialog.show()
    }

    // --- UI HELPERS ---
    private fun createSearchBg() = android.graphics.drawable.GradientDrawable().apply {
        setColor(Color.parseColor("#1A1A1A")); cornerRadius = 20f
    }

    private fun createLabel(t: String) = TextView(this).apply {
        text = t; setTextColor(Color.WHITE); textSize = 20f; setPadding(0, 60, 0, 30); typeface = Typeface.DEFAULT_BOLD
    }

    private fun createMiniPlayer(): View {
        miniPlayer = CardView(this).apply {
            radius = 30f; setCardBackgroundColor(Color.parseColor("#111111"))
            visibility = View.GONE
            layoutParams = RelativeLayout.LayoutParams(-1, -2).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); setMargins(20, 0, 20, 30)
            }
            setOnClickListener { openFullPlayer() }
        }
        val mainLay = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        
        seekBar = SeekBar(this).apply { 
            setPadding(0, 0, 0, 0)
            progressDrawable.setColorFilter(Color.CYAN, PorterDuff.Mode.SRC_IN)
            thumb = null // Barra limpia en el miniplayer
        }

        val controls = LinearLayout(this).apply { 
            gravity = Gravity.CENTER_VERTICAL; setPadding(30, 20, 30, 20) 
        }
        val img = ImageView(this).apply { id = 888; layoutParams = LinearLayout.LayoutParams(110, 110) }
        miniTitle = TextView(this).apply { 
            setTextColor(Color.WHITE); setPadding(30, 0, 0, 0); layoutParams = LinearLayout.LayoutParams(0, -2, 1f); maxLines = 1 
        }
        btnPlay = ImageButton(this).apply { 
            setImageResource(android.R.drawable.ic_media_play); setBackgroundColor(0); setColorFilter(Color.WHITE)
            setOnClickListener { if(player?.isPlaying == true) player?.pause() else player?.play() }
        }

        controls.addView(img); controls.addView(miniTitle); controls.addView(btnPlay)
        mainLay.addView(seekBar); mainLay.addView(controls)
        miniPlayer.addView(mainLay)
        return miniPlayer
    }

    // --- ADAPTERS ---
    inner class SongAdapter(val list: List<Song>, val onClick: (Song) -> Unit) : RecyclerView.Adapter<SongAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v)
        override fun onCreateViewHolder(p: ViewGroup, t: Int): VH {
            val root = LinearLayout(p.context).apply { setPadding(0, 15, 0, 15); gravity = Gravity.CENTER_VERTICAL }
            val img = ImageView(p.context).apply { id = 1; layoutParams = LinearLayout.LayoutParams(120, 120) }
            val txts = LinearLayout(p.context).apply { orientation = LinearLayout.VERTICAL; setPadding(30, 0, 0, 0) }
            val t1 = TextView(p.context).apply { id = 2; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD }
            val t2 = TextView(p.context).apply { id = 3; setTextColor(Color.GRAY); textSize = 12f }
            txts.addView(t1); txts.addView(t2); root.addView(img); root.addView(txts)
            return VH(root)
        }
        override fun onBindViewHolder(h: VH, p: Int) {
            val s = list[p]
            h.itemView.findViewById<ImageView>(1).load(s.cover) { transformations(RoundedCornersTransformation(15f)) }
            h.itemView.findViewById<TextView>(2).text = s.title
            h.itemView.findViewById<TextView>(3).text = s.artist
            h.itemView.setOnClickListener { onClick(s) }
        }
        override fun getItemCount() = list.size
    }

    inner class AlbumAdapter(val list: List<Album>) : RecyclerView.Adapter<AlbumAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v)
        override fun onCreateViewHolder(p: ViewGroup, t: Int): VH {
            val root = LinearLayout(p.context).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 40, 0) }
            val img = ImageView(p.context).apply { id = 1; layoutParams = LinearLayout.LayoutParams(400, 400) }
            val t1 = TextView(p.context).apply { id = 2; setTextColor(Color.WHITE); setPadding(0, 15, 0, 0); maxLines = 1; layoutParams = LinearLayout.LayoutParams(400, -2) }
            root.addView(img); root.addView(t1)
            return VH(root)
        }
        override fun onBindViewHolder(h: VH, p: Int) {
            val a = list[p]
            h.itemView.findViewById<ImageView>(1).load(a.cover) { transformations(RoundedCornersTransformation(25f)) }
            h.itemView.findViewById<TextView>(2).text = a.title
        }
        override fun getItemCount() = list.size
    }
}
