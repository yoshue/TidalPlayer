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

data class Song(val id: String, val title: String, val artist: String, val cover: String, val preview: String)
data class Album(val id: String, val title: String, val cover: String)

class MainActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private var equalizer: Equalizer? = null
    private val songList = mutableListOf<Song>()
    private val albumList = mutableListOf<Album>()
    
    private lateinit var songAdapter: SongAdapter
    private lateinit var albumAdapter: AlbumAdapter
    private lateinit var seekBar: SeekBar
    private lateinit var btnPlay: ImageButton
    private lateinit var miniPlayer: CardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // COLOR DE CONTROL: Si ves este fondo azul oscuro, el código cargó bien
        val root = RelativeLayout(this).apply { setBackgroundColor(Color.parseColor("#00121A")) }
        
        // Interfaz generada dinámicamente
        val content = LinearLayout(this).apply { 
            orientation = LinearLayout.VERTICAL
            setPadding(40, 100, 40, 0)
        }

        val title = TextView(this).apply {
            text = "Tidal Pro v2.0" // MARCADOR DE VERSIÓN
            setTextColor(Color.WHITE); textSize = 30f; typeface = Typeface.DEFAULT_BOLD
        }
        
        val searchInput = EditText(this).apply {
            hint = "Buscar música o álbumes..."; setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE); setPadding(0, 40, 0, 40)
        }

        // Listas
        val rvSongs = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@MainActivity) }
        songAdapter = SongAdapter(songList) { playSong(it) }
        rvSongs.adapter = songAdapter

        content.addView(title); content.addView(searchInput); content.addView(rvSongs)
        
        // Mini Reproductor con SeekBar
        val pView = createPlayerUI()
        
        root.addView(content); root.addView(pView)
        setContentView(root)

        setupPlayer()
        loadCharts() // Carga Deezer por defecto

        searchInput.setOnEditorActionListener { _, _, _ -> 
            searchMusic(searchInput.text.toString()); true 
        }
    }

    private fun loadCharts() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val res = URL("https://api.deezer.com/chart/0").readText()
                val tracks = JSONObject(res).getJSONObject("tracks").getJSONArray("data")
                withContext(Dispatchers.Main) {
                    songList.clear()
                    for (i in 0 until tracks.length()) {
                        val o = tracks.getJSONObject(i)
                        songList.add(Song(o.getString("id"), o.getString("title"), o.getJSONObject("artist").getString("name"), o.getJSONObject("album").getString("cover_medium"), o.getString("preview")))
                    }
                    songAdapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {}
        }
    }

    private fun searchMusic(q: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val res = URL("https://api.deezer.com/search?q=$q").readText()
                val data = JSONObject(res).getJSONArray("data")
                withContext(Dispatchers.Main) {
                    songList.clear()
                    for (i in 0 until data.length()) {
                        val o = data.getJSONObject(i)
                        songList.add(Song(o.getString("id"), o.getString("title"), o.getJSONObject("artist").getString("name"), o.getJSONObject("album").getString("cover_medium"), o.getString("preview")))
                    }
                    songAdapter.notifyDataSetChanged()
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
                    startSeekBarUpdate()
                }
            }
        })
    }

    private fun startSeekBarUpdate() {
        lifecycleScope.launch {
            while(true) {
                player?.let { if(it.isPlaying) seekBar.progress = it.currentPosition.toInt() }
                delay(1000)
            }
        }
    }

    private fun playSong(s: Song) {
        miniPlayer.visibility = View.VISIBLE
        findViewById<TextView>(99).text = s.title
        player?.setMediaItem(MediaItem.fromUri(s.preview))
        player?.prepare(); player?.play()
        btnPlay.setImageResource(android.R.drawable.ic_media_pause)
    }

    private fun createPlayerUI(): View {
        miniPlayer = CardView(this).apply {
            radius = 30f; setCardBackgroundColor(Color.parseColor("#1A1A1A"))
            visibility = View.GONE
            layoutParams = RelativeLayout.LayoutParams(-1, -2).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); setMargins(30, 0, 30, 40)
            }
        }
        val lay = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 20, 40, 30) }
        seekBar = SeekBar(this).apply { setPadding(0, 0, 0, 20) }
        val info = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val txt = TextView(this).apply { id = 99; setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        btnPlay = ImageButton(this).apply { setImageResource(android.R.drawable.ic_media_play); setBackgroundColor(0); setColorFilter(Color.WHITE) }
        
        info.addView(txt); info.addView(btnPlay); lay.addView(seekBar); lay.addView(info)
        miniPlayer.addView(lay)
        return miniPlayer
    }

    inner class SongAdapter(val list: List<Song>, val onClick: (Song) -> Unit) : RecyclerView.Adapter<SongAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v)
        override fun onCreateViewHolder(p: ViewGroup, t: Int): VH {
            val root = LinearLayout(p.context).apply { setPadding(0, 20, 0, 20); gravity = Gravity.CENTER_VERTICAL }
            val img = ImageView(p.context).apply { id = 1; layoutParams = LinearLayout.LayoutParams(120, 120) }
            val t1 = TextView(p.context).apply { id = 2; setTextColor(Color.WHITE); setPadding(30, 0, 0, 0) }
            root.addView(img); root.addView(t1)
            return VH(root)
        }
        override fun onBindViewHolder(h: VH, p: Int) {
            val s = list[p]
            h.itemView.findViewById<ImageView>(1).load(s.cover) { transformations(RoundedCornersTransformation(10f)) }
            h.itemView.findViewById<TextView>(2).text = s.title
            h.itemView.setOnClickListener { onClick(s) }
        }
        override fun getItemCount() = list.size
    }
}
