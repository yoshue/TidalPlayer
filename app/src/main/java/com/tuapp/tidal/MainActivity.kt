package com.tuapp.tidal

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Modelo de la canción
data class Song(val id: String, val title: String, val artist: String, val cover: String)

class MainActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MusicAdapter
    private val songList = mutableListOf<Song>()
    
    // UI components
    private lateinit var miniPlayer: CardView
    private lateinit var miniTitle: TextView
    private lateinit var miniCover: ImageView
    private lateinit var btnPlay: ImageButton
    private lateinit var loader: ProgressBar

    // Lista de servidores ultra-estables (Febrero 2026)
    private val apiServers = arrayOf(
        "https://pipedapi.lunar.icu",
        "https://api.piped.victr.me",
        "https://pipedapi.ducks.party",
        "https://pipedapi.rivo.cc"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Layout Principal (Negro Profundo)
        val root = RelativeLayout(this).apply { setBackgroundColor(Color.BLACK) }

        // 1. Buscador Estilo Spotify
        val searchBar = CardView(this).apply {
            id = View.generateViewId()
            radius = 30f
            setCardBackgroundColor(Color.parseColor("#1A1A1A"))
            layoutParams = RelativeLayout.LayoutParams(-1, -2).apply {
                setMargins(40, 100, 40, 30)
            }
        }
        
        val searchInput = EditText(this).apply {
            hint = "Buscar artistas o canciones..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(40, 30, 40, 30)
            textSize = 15f
        }
        searchBar.addView(searchInput)

        // 2. Grid de Resultados (Cuadrícula de 2 columnas)
        recyclerView = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            setPadding(20, 0, 20, 250) // Espacio para el reproductor
            clipToPadding = false
            layoutParams = RelativeLayout.LayoutParams(-1, -1).apply {
                addRule(RelativeLayout.BELOW, searchBar.id)
            }
        }
        adapter = MusicAdapter(songList) { playSong(it) }
        recyclerView.adapter = adapter

        // 3. Mini Reproductor Inferior
        miniPlayer = CardView(this).apply {
            visibility = View.GONE
            radius = 20f
            setCardBackgroundColor(Color.parseColor("#222222"))
            layoutParams = RelativeLayout.LayoutParams(-1, 160).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                setMargins(30, 0, 30, 40)
            }
        }
        
        val miniLayout = LinearLayout(this).apply { 
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20, 10, 30, 10)
        }
        
        miniCover = ImageView(this).apply { layoutParams = LinearLayout.LayoutParams(120, 120) }
        miniTitle = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 14f; setPadding(30, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f); maxLines = 1
        }
        btnPlay = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_pause)
            setBackgroundColor(Color.TRANSPARENT); setColorFilter(Color.WHITE)
        }
        
        miniLayout.addView(miniCover); miniLayout.addView(miniTitle); miniLayout.addView(btnPlay)
        miniPlayer.addView(miniLayout)

        loader = ProgressBar(this).apply {
            visibility = View.GONE
            layoutParams = RelativeLayout.LayoutParams(-2, -2).apply { addRule(RelativeLayout.CENTER_IN_PARENT) }
        }

        root.addView(searchBar); root.addView(recyclerView); root.addView(miniPlayer); root.addView(loader)
        setContentView(root)

        // Evento de búsqueda
        searchInput.setOnEditorActionListener { v, _, _ ->
            val query = v.text.toString()
            if (query.isNotEmpty()) performSearch(query)
            true
        }

        setupPlayer()
    }

    private fun setupPlayer() {
        player = ExoPlayer.Builder(this).build()
        btnPlay.setOnClickListener {
            player?.let { if (it.isPlaying) it.pause() else it.play() }
            btnPlay.setImageResource(if (player?.isPlaying == true) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        }
    }

    private fun performSearch(query: String) {
        loader.visibility = View.VISIBLE
        songList.clear()
        adapter.notifyDataSetChanged()

        lifecycleScope.launch(Dispatchers.IO) {
            var success = false
            for (server in apiServers) {
                if (success) break
                try {
                    val url = URL("$server/search?q=${URLEncoder.encode(query, "UTF-8")}&filter=music_songs")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.apply { connectTimeout = 5000; readTimeout = 5000; setRequestProperty("User-Agent", "Mozilla/5.0") }

                    if (conn.responseCode == 200) {
                        val response = conn.inputStream.bufferedReader().readText()
                        if (response.trim().startsWith("{")) {
                            val items = JSONObject(response).getJSONArray("items")
                            for (i in 0 until items.length()) {
                                val item = items.getJSONObject(i)
                                val videoId = item.getString("url").split("v=").getOrNull(1) ?: ""
                                songList.add(Song(videoId, item.getString("title"), item.getString("uploaderName"), item.getString("thumbnail")))
                            }
                            success = true
                        }
                    }
                } catch (e: Exception) { continue }
            }

            withContext(Dispatchers.Main) {
                loader.visibility = View.GONE
                if (!success) Toast.makeText(this@MainActivity, "Error de conexión. Intenta de nuevo.", Toast.LENGTH_SHORT).show()
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun playSong(song: Song) {
        miniPlayer.visibility = View.VISIBLE
        miniTitle.text = song.title
        miniCover.load(song.cover)
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Buscamos el stream en el primer servidor disponible
                val streamUrl = URL("${apiServers[0]}/streams/${song.id}")
                val json = JSONObject(streamUrl.readText())
                val audioUrl = json.getJSONArray("audioStreams").getJSONObject(0).getString("url")
                
                withContext(Dispatchers.Main) {
                    player?.setMediaItem(MediaItem.fromUri(audioUrl))
                    player?.prepare(); player?.play()
                }
            } catch (e: Exception) { }
        }
    }

    // --- ADAPTADOR DE CUADRÍCULA ---
    inner class MusicAdapter(val list: List<Song>, val onClick: (Song) -> Unit) : RecyclerView.Adapter<MusicAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val img = v.findViewById<ImageView>(1)
            val title = v.findViewById<TextView>(2)
            val artist = v.findViewById<TextView>(3)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val card = CardView(parent.context).apply {
                radius = 20f; setCardBackgroundColor(Color.parseColor("#111111"))
                layoutParams = GridLayoutManager.LayoutParams(-1, -2).apply { setMargins(15, 15, 15, 15) }
            }
            val lay = LinearLayout(parent.context).apply { orientation = LinearLayout.VERTICAL }
            val iv = ImageView(parent.context).apply { id = 1; layoutParams = LinearLayout.LayoutParams(-1, 400); scaleType = ImageView.ScaleType.CENTER_CROP }
            val t1 = TextView(parent.context).apply { id = 2; setTextColor(Color.WHITE); textSize = 14f; setPadding(10, 10, 10, 0); maxLines = 1; typeface = Typeface.DEFAULT_BOLD }
            val t2 = TextView(parent.context).apply { id = 3; setTextColor(Color.GRAY); textSize = 12f; setPadding(10, 0, 10, 20) }
            lay.addView(iv); lay.addView(t1); lay.addView(t2); card.addView(lay)
            return VH(card)
        }

        override fun onBindViewHolder(h: VH, p: Int) {
            val s = list[p]
            h.img.load(s.cover); h.title.text = s.title; h.artist.text = s.artist
            h.itemView.setOnClickListener { onClick(s) }
        }
        override fun getItemCount() = list.size
    }
}
