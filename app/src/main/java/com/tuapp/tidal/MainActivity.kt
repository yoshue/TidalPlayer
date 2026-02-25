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
    private lateinit var miniCover: ImageView
    private lateinit var btnPlay: ImageButton
    private lateinit var loader: ProgressBar

    // Servidores actualizados para evitar el error "Value <html>..."
    private val apiServers = arrayOf(
        "https://pipedapi.lunar.icu",
        "https://api.piped.victr.me",
        "https://pipedapi.ducks.party"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val root = RelativeLayout(this).apply { setBackgroundColor(Color.BLACK) }

        // 1. Cabecera con Título "Explorar"
        val header = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.VERTICAL
            setPadding(50, 120, 50, 20) // Bajamos el buscador para que no se tape
            layoutParams = RelativeLayout.LayoutParams(-1, -2)
        }

        val titleText = TextView(this).apply {
            text = "Explorar"
            setTextColor(Color.WHITE)
            textSize = 26f
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            setPadding(0, 0, 0, 40)
        }
        header.addView(titleText)

        // 2. BUSCADOR CON BOTÓN VISIBLE (Tarjeta)
        val searchCard = CardView(this).apply {
            radius = 25f
            setCardBackgroundColor(Color.parseColor("#1A1A1A"))
            elevation = 0f
        }
        
        val searchLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(30, 5, 20, 5)
        }

        val searchInput = EditText(this).apply {
            hint = "Artistas o canciones..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            setSingleLine()
        }

        // AQUÍ ESTÁ EL BOTÓN QUE FALTABA
        val searchBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_search) // Ícono de lupa
            setColorFilter(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(20, 20, 20, 20)
        }

        searchLayout.addView(searchInput)
        searchLayout.addView(searchBtn)
        searchCard.addView(searchLayout)
        header.addView(searchCard)

        // 3. Grid de Resultados
        recyclerView = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            setPadding(20, 30, 20, 250)
            clipToPadding = false
            layoutParams = RelativeLayout.LayoutParams(-1, -1).apply {
                addRule(RelativeLayout.BELOW, header.id)
            }
        }
        adapter = MusicAdapter(songList) { playSong(it) }
        recyclerView.adapter = adapter

        // 4. Mini Reproductor
        miniPlayer = CardView(this).apply {
            visibility = View.GONE
            radius = 30f
            setCardBackgroundColor(Color.parseColor("#252525"))
            layoutParams = RelativeLayout.LayoutParams(-1, 160).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                setMargins(40, 0, 40, 60)
            }
        }
        
        val miniLayout = LinearLayout(this).apply { 
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(25, 0, 40, 0)
        }
        
        miniCover = ImageView(this).apply { layoutParams = LinearLayout.LayoutParams(110, 110) }
        miniTitle = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 14f; setPadding(30, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f); maxLines = 1; typeface = Typeface.DEFAULT_BOLD
        }
        btnPlay = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_pause)
            setBackgroundColor(Color.TRANSPARENT); setColorFilter(Color.WHITE); scaleX = 1.2f; scaleY = 1.2f
        }
        
        miniLayout.addView(miniCover); miniLayout.addView(miniTitle); miniLayout.addView(btnPlay)
        miniPlayer.addView(miniLayout)

        loader = ProgressBar(this).apply {
            visibility = View.GONE
            layoutParams = RelativeLayout.LayoutParams(-2, -2).apply { addRule(RelativeLayout.CENTER_IN_PARENT) }
        }

        root.addView(header); root.addView(recyclerView); root.addView(miniPlayer); root.addView(loader)
        setContentView(root)

        // Acciones de búsqueda (Tanto botón como teclado)
        searchBtn.setOnClickListener {
            val q = searchInput.text.toString()
            if (q.isNotEmpty()) performSearch(q)
        }

        searchInput.setOnEditorActionListener { _, _, _ ->
            searchBtn.performClick()
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
            var found = false
            for (server in apiServers) {
                try {
                    val url = URL("$server/search?q=${URLEncoder.encode(query, "UTF-8")}&filter=music_songs")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 6000
                    
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
                if (!found) Toast.makeText(this@MainActivity, "Servidores saturados. Intenta de nuevo.", Toast.LENGTH_SHORT).show()
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
                // Probamos obtener el stream del primer servidor que responda
                val streamUrl = URL("${apiServers[0]}/streams/${song.id}")
                val audioUrl = JSONObject(streamUrl.readText()).getJSONArray("audioStreams").getJSONObject(0).getString("url")
                
                withContext(Dispatchers.Main) {
                    player?.setMediaItem(MediaItem.fromUri(audioUrl))
                    player?.prepare(); player?.play()
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
                radius = 30f; setCardBackgroundColor(Color.parseColor("#0A0A0A"))
                layoutParams = GridLayoutManager.LayoutParams(-1, -2).apply { setMargins(12, 12, 12, 12) }
            }
            val lay = LinearLayout(parent.context).apply { orientation = LinearLayout.VERTICAL }
            val iv = ImageView(parent.context).apply { id = 1; layoutParams = LinearLayout.LayoutParams(-1, 420); scaleType = ImageView.ScaleType.CENTER_CROP }
            val tv1 = TextView(parent.context).apply { id = 2; setTextColor(Color.WHITE); textSize = 13f; setPadding(15, 15, 15, 0); maxLines = 1; typeface = Typeface.DEFAULT_BOLD }
            val tv2 = TextView(parent.context).apply { id = 3; setTextColor(Color.GRAY); textSize = 11f; setPadding(15, 0, 15, 20) }
            lay.addView(iv); lay.addView(tv1); lay.addView(tv2); card.addView(lay)
            return VH(card)
        }

        override fun onBindViewHolder(h: VH, p: Int) {
            val s = list[p]
            h.img.load(s.cover); h.t1.text = s.title; h.t2.text = s.artist
            h.itemView.setOnClickListener { onClick(s) }
        }
        override fun getItemCount() = list.size
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
    }
}
