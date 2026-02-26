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
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Song(val id: String, val title: String, val artist: String, val cover: String)

class MainActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MusicAdapter
    private val songList = mutableListOf<Song>()
    
    private lateinit var miniPlayer: CardView
    private lateinit var miniTitle: TextView
    private lateinit var miniArtist: TextView
    private lateinit var miniCover: ImageView
    private lateinit var btnPlay: ImageButton
    private lateinit var loader: ProgressBar

    // INSTANCIAS DE ALTO RENDIMIENTO (Actualizadas 2026)
    private val apiServers = arrayOf(
        "https://pipedapi.lunar.icu",
        "https://pipedapi-libre.kavin.rocks",
        "https://api.piped.victr.me",
        "https://pipedapi.us.to",
        "https://pipedapi.aeong.one"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Contenedor principal con fondo gris muy oscuro (Estilo Tidal)
        val root = RelativeLayout(this).apply { setBackgroundColor(Color.parseColor("#0A0A0A")) }

        // 1. Encabezado Estilizado
        val header = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.VERTICAL
            setPadding(60, 130, 60, 30)
            layoutParams = RelativeLayout.LayoutParams(-1, -2)
        }

        val titleText = TextView(this).apply {
            text = "Música"
            setTextColor(Color.WHITE)
            textSize = 32f
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            setPadding(0, 0, 0, 40)
        }
        header.addView(titleText)

        // 2. Buscador Moderno
        val searchCard = CardView(this).apply {
            radius = 15f
            setCardBackgroundColor(Color.parseColor("#1C1C1C"))
            elevation = 0f
        }
        
        val searchLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(35, 10, 20, 10)
        }

        val searchInput = EditText(this).apply {
            hint = "Artistas, álbumes o canciones"
            setHintTextColor(Color.parseColor("#555555"))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            setSingleLine()
        }

        val searchBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_search)
            setColorFilter(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(20, 20, 20, 20)
        }

        searchLayout.addView(searchInput)
        searchLayout.addView(searchBtn)
        searchCard.addView(searchLayout)
        header.addView(searchCard)

        // 3. Grid de Álbumes/Canciones
        recyclerView = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            setPadding(30, 30, 30, 300)
            clipToPadding = false
            layoutParams = RelativeLayout.LayoutParams(-1, -1).apply {
                addRule(RelativeLayout.BELOW, header.id)
            }
        }
        adapter = MusicAdapter(songList) { playSong(it) }
        recyclerView.adapter = adapter

        // 4. Reproductor Inferior (Flotante Premium)
        miniPlayer = CardView(this).apply {
            visibility = View.GONE
            radius = 25f
            setCardBackgroundColor(Color.parseColor("#222222"))
            elevation = 20f
            layoutParams = RelativeLayout.LayoutParams(-1, 170).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                setMargins(40, 0, 40, 60)
            }
        }
        
        val miniLayout = LinearLayout(this).apply { 
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(30, 0, 40, 0)
        }
        
        miniCover = ImageView(this).apply { 
            layoutParams = LinearLayout.LayoutParams(110, 110)
        }
        
        val miniTexts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        
        miniTitle = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 14f; maxLines = 1; typeface = Typeface.DEFAULT_BOLD
        }
        miniArtist = TextView(this).apply {
            setTextColor(Color.parseColor("#AAAAAA")); textSize = 11f; maxLines = 1
        }
        miniTexts.addView(miniTitle); miniTexts.addView(miniArtist)

        btnPlay = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_pause)
            setBackgroundColor(Color.TRANSPARENT); setColorFilter(Color.WHITE); scaleX = 1.4f; scaleY = 1.4f
        }
        
        miniLayout.addView(miniCover); miniLayout.addView(miniTexts); miniLayout.addView(btnPlay)
        miniPlayer.addView(miniLayout)

        loader = ProgressBar(this).apply {
            visibility = View.GONE
            layoutParams = RelativeLayout.LayoutParams(-2, -2).apply { addRule(RelativeLayout.CENTER_IN_PARENT) }
        }

        root.addView(header); root.addView(recyclerView); root.addView(miniPlayer); root.addView(loader)
        setContentView(root)

        searchBtn.setOnClickListener {
            val q = searchInput.text.toString()
            if (q.isNotEmpty()) performSearch(q)
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
            var found = false
            for (server in apiServers) {
                try {
                    val url = URL("$server/search?q=${URLEncoder.encode(query, "UTF-8")}&filter=music_songs")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 8000
                    // User-Agent de iPhone para saltar bloqueos
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.0 Mobile/15E148 Safari/604.1")
                    
                    if (conn.responseCode == 200) {
                        val text = conn.inputStream.bufferedReader().readText()
                        if (text.trim().startsWith("{")) {
                            val items = JSONObject(text).getJSONArray("items")
                            for (i in 0 until items.length()) {
                                val item = items.getJSONObject(i)
                                val vId = item.getString("url").split("v=").getOrNull(1) ?: ""
                                songList.add(Song(vId, item.getString("title"), item.getString("uploaderName"), item.getString("thumbnail")))
                            }
                            found = true
                            break
                        }
                    }
                } catch (e: Exception) { continue }
            }

            withContext(Dispatchers.Main) {
                loader.visibility = View.GONE
                if (!found) Toast.makeText(this@MainActivity, "Servidores ocupados. Intenta con otro nombre.", Toast.LENGTH_LONG).show()
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun playSong(song: Song) {
        miniPlayer.visibility = View.VISIBLE
        miniTitle.text = song.title
        miniArtist.text = song.artist
        miniCover.load(song.cover) { transformations(RoundedCornersTransformation(15f)) }
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Probamos obtener el audio de una instancia robusta
                val streamUrl = URL("${apiServers[0]}/streams/${song.id}")
                val conn = streamUrl.openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                val audioUrl = JSONObject(conn.inputStream.bufferedReader().readText()).getJSONArray("audioStreams").getJSONObject(0).getString("url")
                
                withContext(Dispatchers.Main) {
                    player?.setMediaItem(MediaItem.fromUri(audioUrl))
                    player?.prepare(); player?.play()
                    btnPlay.setImageResource(android.R.drawable.ic_media_pause)
                }
            } catch (e: Exception) { }
        }
    }

    inner class MusicAdapter(val list: List<Song>, val onClick: (Song) -> Unit) : RecyclerView.Adapter<MusicAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val img = v.findViewById<ImageView>(1)
            val t1 = v.findViewById<TextView>(2)
            val t2 = v.findViewById<TextView>(3)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val card = CardView(parent.context).apply {
                radius = 20f; setCardBackgroundColor(Color.TRANSPARENT); elevation = 0f
                layoutParams = GridLayoutManager.LayoutParams(-1, -2).apply { setMargins(15, 15, 15, 15) }
            }
            val lay = LinearLayout(parent.context).apply { orientation = LinearLayout.VERTICAL }
            val iv = ImageView(parent.context).apply { id = 1; layoutParams = LinearLayout.LayoutParams(-1, 450); scaleType = ImageView.ScaleType.CENTER_CROP }
            val tv1 = TextView(parent.context).apply { id = 2; setTextColor(Color.WHITE); textSize = 14f; setPadding(10, 20, 10, 0); maxLines = 1; typeface = Typeface.DEFAULT_BOLD }
            val tv2 = TextView(parent.context).apply { id = 3; setTextColor(Color.parseColor("#777777")); textSize = 12f; setPadding(10, 5, 10, 30); maxLines = 1 }
            lay.addView(iv); lay.addView(tv1); lay.addView(tv2); card.addView(lay)
            return VH(card)
        }

        override fun onBindViewHolder(h: VH, p: Int) {
            val s = list[p]
            h.img.load(s.cover) { transformations(RoundedCornersTransformation(25f)) }
            h.t1.text = s.title; h.t2.text = s.artist
            h.itemView.setOnClickListener { onClick(s) }
        }
        override fun getItemCount() = list.size
    }
}
