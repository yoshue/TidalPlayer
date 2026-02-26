package com.tuapp.tidal

import android.graphics.*
import android.media.audiofx.Equalizer
import android.os.Bundle
import android.view.*
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
import java.util.Scanner

// --- MODELOS DE DATOS ---
data class Song(val id: String, val title: String, val artist: String, val cover: String, val preview: String)
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
    private var currentPlayingIndex = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // UI BASE
        val root = RelativeLayout(this).apply { setBackgroundColor(Color.parseColor("#080808")) }
        
        val scroll = androidx.core.widget.NestedScrollView(this).apply {
            layoutParams = RelativeLayout.LayoutParams(-1, -1)
        }
        
        val content = LinearLayout(this).apply { 
            orientation = LinearLayout.VERTICAL
            setPadding(40, 120, 40, 300) 
        }

        // Cabecera
        val header = TextView(this).apply {
            text = "Explorar v3"; setTextColor(Color.WHITE); textSize = 32f; typeface = Typeface.DEFAULT_BOLD
        }
        
        val searchInput = EditText(this).apply {
            hint = "Artistas, canciones o álbumes"; setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE); setPadding(20, 40, 20, 40)
            setBackgroundColor(Color.parseColor("#121212"))
        }

        // Listas
        val rvSongs = RecyclerView(this).apply { 
            layoutManager = LinearLayoutManager(this@MainActivity)
            isNestedScrollingEnabled = false 
        }
        songAdapter = SongAdapter(songList) { playSong(it) }
        rvSongs.adapter = songAdapter

        val albumLabel = TextView(this).apply { 
            text = "Álbumes Sugeridos"; setTextColor(Color.WHITE); setPadding(0, 50, 0, 20); textSize = 18f 
        }
        val rvAlbums = RecyclerView(this).apply { 
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
        }
        albumAdapter = AlbumAdapter(albumList)
        rvAlbums.adapter = albumAdapter

        content.addView(header); content.addView(searchInput); content.addView(rvSongs); content.addView(albumLabel); content.addView(rvAlbums)
        scroll.addView(content)
        
        // Mini Player
        val pView = createPlayerControl()
        
        root.addView(scroll); root.addView(pView)
        setContentView(root)

        setupPlayer()
        loadInitialData()

        searchInput.setOnEditorActionListener { _, _, _ -> 
            searchEverything(searchInput.text.toString()); true 
        }
    }

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
                    songAdapter.notifyDataSetChanged()
                    albumAdapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {}
        }
    }

    private fun searchEverything(q: String) {
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

    private fun setupPlayer() {
        player = ExoPlayer.Builder(this).build()
        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(s: Int) {
                if (s == Player.STATE_READY) {
                    seekBar.max = player?.duration?.toInt() ?: 0
                    updateProgress()
                    initEq()
                }
            }
        })
    }

    private fun initEq() {
        if (equalizer == null) {
            equalizer = Equalizer(0, player?.audioSessionId ?: 0).apply { enabled = true }
        }
    }

    private fun updateProgress() {
        lifecycleScope.launch {
            while(true) {
                player?.let { if(it.isPlaying) seekBar.progress = it.currentPosition.toInt() }
                delay(1000)
            }
        }
    }

    private fun playSong(s: Song) {
        miniPlayer.visibility = View.VISIBLE
        miniTitle.text = s.title
        player?.setMediaItem(MediaItem.fromUri(s.preview))
        player?.prepare(); player?.play()
        btnPlay.setImageResource(android.R.drawable.ic_media_pause)
    }

    private fun createPlayerControl(): View {
        miniPlayer = CardView(this).apply {
            radius = 30f; setCardBackgroundColor(Color.parseColor("#151515"))
            visibility = View.GONE
            layoutParams = RelativeLayout.LayoutParams(-1, -2).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); setMargins(30, 0, 30, 40)
            }
        }
        val lay = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 20, 40, 30) }
        seekBar = SeekBar(this).apply { setPadding(0, 0, 0, 20) }
        
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        miniTitle = TextView(this).apply { setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        
        val btnPrev = ImageButton(this).apply { setImageResource(android.R.drawable.ic_media_previous); setBackgroundColor(0); setColorFilter(Color.WHITE)
            setOnClickListener { /* Lógica de anterior */ }
        }
        btnPlay = ImageButton(this).apply { setImageResource(android.R.drawable.ic_media_play); setBackgroundColor(0); setColorFilter(Color.WHITE); scaleX = 1.2f; scaleY = 1.2f
            setOnClickListener { if(player?.isPlaying == true) player?.pause() else player?.play() }
        }
        val btnNext = ImageButton(this).apply { setImageResource(android.R.drawable.ic_media_next); setBackgroundColor(0); setColorFilter(Color.WHITE) 
            setOnClickListener { /* Lógica de siguiente */ }
        }

        controls.addView(miniTitle); controls.addView(btnPrev); controls.addView(btnPlay); controls.addView(btnNext)
        lay.addView(seekBar); lay.addView(controls)
        miniPlayer.addView(lay)
        return miniPlayer
    }

    // --- ADAPTADORES ---
    inner class SongAdapter(val list: List<Song>, val onClick: (Song) -> Unit) : RecyclerView.Adapter<SongAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v)
        override fun onCreateViewHolder(p: ViewGroup, t: Int): VH {
            val root = LinearLayout(p.context).apply { setPadding(0, 20, 0, 20); gravity = Gravity.CENTER_VERTICAL }
            val img = ImageView(p.context).apply { id = 1; layoutParams = LinearLayout.LayoutParams(130, 130) }
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
            val root = LinearLayout(p.context).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 30, 0) }
            val img = ImageView(p.context).apply { id = 1; layoutParams = LinearLayout.LayoutParams(300, 300) }
            val t1 = TextView(p.context).apply { id = 2; setTextColor(Color.WHITE); textSize = 12f; setPadding(0, 10, 0, 0); maxLines = 1; layoutParams = LinearLayout.LayoutParams(300, -2) }
            root.addView(img); root.addView(t1)
            return VH(root)
        }
        override fun onBindViewHolder(h: VH, p: Int) {
            val a = list[p]
            h.itemView.findViewById<ImageView>(1).load(a.cover) { transformations(RoundedCornersTransformation(20f)) }
            h.itemView.findViewById<TextView>(2).text = a.title
        }
        override fun getItemCount() = list.size
    }
}
